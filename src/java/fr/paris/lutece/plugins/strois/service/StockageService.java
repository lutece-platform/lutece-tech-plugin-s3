/*
 * Copyright (c) 2002-2024, City of Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */

package fr.paris.lutece.plugins.strois.service;

import fr.paris.lutece.plugins.strois.util.S3Util;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import okhttp3.OkHttpClient;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class StockageService
{
    private MinioClient _s3Client;

    // Properties
    private final String _s3Url;
    private final String _s3Bucket;
    private final String _s3Key;
    private final String _s3Password;

    private static final String SLASH = "/";
    private static final String DOUBLE_SLASH = "//";

    public StockageService( String s3Url, String s3Bucket, String s3Key, String s3Password )
    {
        _s3Url = s3Url;
        _s3Bucket = s3Bucket;
        _s3Key = s3Key;
        _s3Password = s3Password;
    }

    /**
     * Get S3 client to interact with NetApp server.
     * 
     * @return _s3Client
     */
    private MinioClient getS3Client( ) throws URISyntaxException
    {
        if ( _s3Client == null )
        {
            OkHttpClient okHttpClient = new OkHttpClient.Builder( ).proxy( getHttpAccessProxy( _s3Url ) ).build( );

            _s3Client = MinioClient.builder( ).endpoint( _s3Url ).credentials( _s3Key, _s3Password ).httpClient( okHttpClient ).build( );
        }

        return _s3Client;
    }

    /**
     * Get proxy from httpaccess conf
     * @param s3Url url to match to "noProxyFor"
     * @return Proxy or Proxy.NO_PROXY
     * @throws URISyntaxException
     */
    private Proxy getHttpAccessProxy( String s3Url ) throws URISyntaxException
    {
        String strProxyHost = AppPropertiesService.getProperty( "httpAccess.proxyHost" );
        int proxyPort = AppPropertiesService.getPropertyInt( "httpAccess.proxyPort", 8080 );
        String strNoProxyFor = AppPropertiesService.getProperty( "httpAccess.noProxyFor" );

        if ( StringUtils.isEmpty( strProxyHost ) )
        {
            return Proxy.NO_PROXY;
        }

        boolean bNoProxy = StringUtils.isNotBlank( strNoProxyFor ) && S3Util.matchesList( strNoProxyFor.split( "," ), new URI( s3Url ).getHost( ) );

        if ( !bNoProxy )
        {
            InetSocketAddress proxyAddr = new InetSocketAddress( strProxyHost, proxyPort );
            return new Proxy( Proxy.Type.HTTP, proxyAddr );
        }
        return Proxy.NO_PROXY;
    }

    /**
     * Load file from NetApp server
     *
     * @param pathToFile path to find file
     * @return byte[] found
     */
    public byte[] loadFileFromNetAppServeur( String pathToFile ) throws MinioException
    {
        String completePathToFile = normalizeS3Path( pathToFile );
        try ( InputStream is = getS3Client( ).getObject( GetObjectArgs.builder( ).bucket( _s3Bucket ).object( completePathToFile ).build( ) ) )
        {
            ByteArrayOutputStream output = new ByteArrayOutputStream( );
            IOUtils.copy( is, output );
            return output.toByteArray( );
        }
        catch( InvalidKeyException | IOException | NoSuchAlgorithmException | URISyntaxException e )
        {
            AppLogService.error( "Erreur chargement du fichier " + pathToFile, e );
            throw new io.minio.errors.MinioException( "Erreur chargement du fichier " + pathToFile );
        }
        catch ( ErrorResponseException e )
        {
            AppLogService.error( "Erreur chargement du fichier " + pathToFile, e );
            logErrorResponse( e );
            throw new MinioException( "Erreur chargement du fichier " + pathToFile );
        }
        catch ( MinioException e )
        {
            AppLogService.error( "Erreur chargement du fichier " + pathToFile, e );
            logMinioException( e );
            throw new MinioException( "Erreur chargement du fichier " + pathToFile );
        }
    }

    /**
     * Proceed save file.
     *
     * @param fileToSave
     *            file content as byte[]
     * @param pathToFile
     *            path + filename to put file content in
     *
     * @return path to the photo on NetApp serveur
     *
     */
    public String saveFileToNetAppServer( byte [ ] fileToSave, String pathToFile ) throws MinioException
    {
        if ( fileToSave == null || StringUtils.isEmpty( pathToFile ) )
        {
            return null;
        }

        String completePathToFile = normalizeS3Path( pathToFile );
        try
        {
            getS3Client( ).putObject( PutObjectArgs.builder( ).bucket( _s3Bucket ).object( completePathToFile )
                    .stream( new ByteArrayInputStream( fileToSave ), fileToSave.length, -1 ).build( ) );
        }
        catch( InvalidKeyException | IOException | NoSuchAlgorithmException | URISyntaxException e )
        {
            AppLogService.error( "Erreur de sauvegarde du fichier " + completePathToFile, e );
            throw new MinioException( "Erreur de sauvegarde du fichier " + completePathToFile );
        }
        catch ( ErrorResponseException e )
        {
            AppLogService.error( "Erreur de sauvegarde du fichier " + completePathToFile, e );
            logErrorResponse( e );
            throw new MinioException( "Erreur de sauvegarde du fichier " + completePathToFile );
        }
        catch ( MinioException e )
        {
            AppLogService.error( "Erreur de sauvegarde du fichier " + completePathToFile, e );
            logMinioException( e );
            throw new MinioException( "Erreur de sauvegarde du fichier " + completePathToFile );
        }

        return completePathToFile;
    }

    /**
     * Delete file on NetApp server.
     * 
     * @param pathToFile
     *            file to delete, complete with file name
     * @return false if error
     */
    public boolean deleteFileOnNetAppServeur( String pathToFile )
    {

        if ( StringUtils.isEmpty( pathToFile ) )
        {
            AppLogService.debug( "Cannot delete file, pathToFile null or empty" );
            return false;
        }

        boolean result = true;

        String completePathToFile = normalizeS3Path( pathToFile );
        AppLogService.debug( "File to delete " + completePathToFile );
        try
        {
            getS3Client( ).removeObject( RemoveObjectArgs.builder( ).bucket( _s3Bucket ).object( completePathToFile ).build( ) );
        }
        catch( InvalidKeyException | IOException | NoSuchAlgorithmException | URISyntaxException e )
        {
            result = false;
            AppLogService.error( "Erreur à la supression du fichier " + completePathToFile + " sur NetApp", e );
        }
        catch ( ErrorResponseException e )
        {
            logErrorResponse( e );
            result = false;
            AppLogService.error( "Erreur à la supression du fichier " + completePathToFile + " sur NetApp", e );
        }
        catch ( MinioException e )
        {
            logMinioException( e );
            result = false;
            AppLogService.error( "Erreur à la supression du fichier " + completePathToFile + " sur NetApp", e );
        }

        AppLogService.debug( "Deleting file " + completePathToFile + " is " + ( result ? "OK" : "KO" ) );
        return result;
    }

    /**
     * Enables HTTP call tracing and written to traceStream.
     *
     * @param traceStream {@link OutputStream} for writing HTTP call tracing.
     * @see #traceOff
     */
    public void traceOn( OutputStream traceStream ) throws URISyntaxException
    {
        getS3Client( ).traceOn( traceStream );
    }

    /**
     * Disables HTTP call tracing previously enabled.
     *
     * @see #traceOn
     * @throws IOException upon connection error
     */
    public void traceOff( ) throws IOException, URISyntaxException
    {
        getS3Client( ).traceOff( );
    }

    /**
     * Sets HTTP connect, write and read timeouts. A value of 0 means no timeout, otherwise values
     * must be between 1 and Integer.MAX_VALUE when converted to milliseconds.
     *
     * <pre>Example:{@code
     * setTimeout(TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(10),
     *     TimeUnit.SECONDS.toMillis(30));
     * }</pre>
     *
     * @param connectTimeout HTTP connect timeout in milliseconds.
     * @param writeTimeout HTTP write timeout in milliseconds.
     * @param readTimeout HTTP read timeout in milliseconds.
     */
    public void setTimeout(long connectTimeout, long writeTimeout, long readTimeout) throws URISyntaxException
    {
        getS3Client( ).setTimeout( connectTimeout, writeTimeout, readTimeout );
    }

    private void logErrorResponse( ErrorResponseException e )
    {
        if ( e.errorResponse( ) != null )
        {
            AppLogService.debug( "errorResponse \n" + e.errorResponse( ) );
        }
        if ( e.httpTrace( ) != null )
        {
            AppLogService.debug( "httpTrace \n" + e.httpTrace( ) );
        }
        if ( e.getCause( ) != null )
        {
            AppLogService.debug( "Cause \n" + e.getCause( ) );
        }
    }
    private void logMinioException( MinioException e )
    {
        if ( e.httpTrace( ) != null )
        {
            AppLogService.debug( "httpTrace \n" + e.httpTrace( ) );
        }
        if ( e.getCause( ) != null )
        {
            AppLogService.debug( "Cause \n" + e.getCause( ) );
        }
    }

    /**
     * Replace "//" with "/" and delete leading "/" if exists
     * @param path
     * @return
     */
    private String normalizeS3Path( String path )
    {
        path = RegExUtils.replaceAll( path, DOUBLE_SLASH, SLASH );
        path = StringUtils.removeStart( path, SLASH );
        return path;
    }

    @Override
    public String toString( )
    {
        return "StockageService{" +
                       "s3Url='" + _s3Url + '\'' +
                       ", s3Bucket='" + _s3Bucket + '\'' +
                       ", s3Key='" + StringUtils.abbreviate( _s3Key, 7 ) + '\'' +
                       '}';
    }
}
