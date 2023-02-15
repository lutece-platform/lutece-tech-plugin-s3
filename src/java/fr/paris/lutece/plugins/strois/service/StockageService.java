/*
 * Copyright (c) 2002-2023, City of Paris
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
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.MinioException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class StockageService
{
    private static MinioClient _s3Client;

    // Properties
    private final String _s3Url;
    private final String _s3Bucket;
    private final String _s3Key;
    private final String _s3Password;
    private final String _s3BasePath;


    public StockageService( String s3Url, String s3Bucket, String s3Key, String s3Password, String s3BasePath )
    {
        _s3Url = s3Url;
        _s3Bucket = s3Bucket;
        _s3Key = s3Key;
        _s3Password = s3Password;
        _s3BasePath = s3BasePath;
    }

    /**
     * Get S3 client to interact with NetApp server.
     * @return _s3Client
     */
    private MinioClient getS3Client( ) throws URISyntaxException
    {
        if ( _s3Client == null )
        {
            OkHttpClient okHttpClient = new OkHttpClient.Builder(  ).proxy( getHttpAccessProxy( _s3Url ) ).build( );

            _s3Client = MinioClient.builder( ).endpoint( _s3Url )
                                .credentials( _s3Key, _s3Password ).httpClient( okHttpClient ).build( );
        }

        return _s3Client;
    }

    private Proxy getHttpAccessProxy( String s3Url ) throws URISyntaxException
    {
        String strProxyHost = AppPropertiesService.getProperty("httpAccess.proxyHost");
        int proxyPort = AppPropertiesService.getPropertyInt("httpAccess.proxyPort", 8080);
        String strNoProxyFor = AppPropertiesService.getProperty("httpAccess.noProxyFor");

        boolean bNoProxy = StringUtils.isNotBlank( strNoProxyFor ) && S3Util.matchesList( strNoProxyFor.split( "," ), new URI( s3Url ).getHost( ) );

        if ( !bNoProxy && StringUtils.isNotEmpty( strProxyHost ) )
        {
            InetSocketAddress proxyAddr = new InetSocketAddress(strProxyHost, proxyPort);
            return new Proxy( Proxy.Type.HTTP, proxyAddr );
        }
        return Proxy.NO_PROXY;
    }

    /**
     * Load file from NetApp server
     * @param pathToFile
     *          path to find file
     * @return IS find
     */
    public InputStream loadFileFromNetAppServeur( String pathToFile ) throws MinioException
    {
        String completePathToFile = pathToFile.replaceAll( "//", "/" );
        try ( InputStream is = getS3Client( ).getObject( GetObjectArgs.builder( ).bucket( _s3Bucket ).object(  completePathToFile ).build( ) ) )
        {
            return is;
        } catch ( InvalidKeyException | ErrorResponseException | InsufficientDataException | InternalException |
                  InvalidResponseException | NoSuchAlgorithmException | ServerException
                  | XmlParserException | IllegalArgumentException | IOException | URISyntaxException e )
        {
            AppLogService.error( "Erreur chargement du fichier " + pathToFile, e );
            throw new io.minio.errors.MinioException( "Erreur chargement du fichier " + pathToFile );
        }
    }
    /**
     * Load file from NetApp server
     * @param pathToFile
     *          path to find file (method prepends s3BasePath)
     * @return IS found
     */
    public InputStream loadFileFromNetAppServeurPrependBasePath( String pathToFile ) throws MinioException
    {
        String completePathToFile = _s3BasePath + pathToFile;
        return loadFileFromNetAppServeur( completePathToFile );
    }

    /**
     * Proceed save file.
     *
     * @param fileToSave file content
     * @param pathToFile path + filename to put file content in
     *
     * @return path to the photo on NetApp serveur
     *
     */
    public  String saveFileToNetAppServer( byte[] fileToSave, String pathToFile ) throws MinioException
    {
        if ( fileToSave == null || StringUtils.isEmpty( pathToFile) )
        {
            return null;
        }

        String completePathToFile = _s3BasePath + pathToFile;
        completePathToFile = completePathToFile.replaceAll( "//", "/" );
        if(completePathToFile.startsWith( "/" )) {
            //remove first char if is /
            completePathToFile = completePathToFile.substring( 1 );
        }
        try
        {
            getS3Client( ).putObject( PutObjectArgs.builder( ).bucket( _s3Bucket).object( completePathToFile )
                                              .stream( new ByteArrayInputStream( fileToSave ), fileToSave.length, -1 ).build( ) );
        }
        catch ( ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException |
                XmlParserException | URISyntaxException e )
        {
            AppLogService.error( "Erreur de sauvegarde du fichier " + completePathToFile, e );
            throw new MinioException( "Erreur de sauvegarde du fichier " + completePathToFile );
        }

        return completePathToFile;
    }
    /**
     * Proceed save file.
     *
     * @param fileToSave file content
     * @param pathToFile path + filename to put file content in (method prepends s3BasePath)
     *
     * @return path to the photo on NetApp serveur
     *
     */
    public  String saveFileToNetAppServerPrependBasePath( byte[] fileToSave, String pathToFile ) throws MinioException
    {
        if ( fileToSave == null || StringUtils.isEmpty( pathToFile ) )
        {
            return null;
        }

        String completePathToFile = _s3BasePath + pathToFile;
        return saveFileToNetAppServer( fileToSave, completePathToFile );
    }

    /**
     * Delete file on NetApp server.
     * @param pathToFile
     *         file to delete, complete with file name
     * @return false if error
     */
    public boolean deleteFileOnNetAppServeur( String pathToFile )
    {

        if ( StringUtils.isEmpty( pathToFile ) )
        {
            AppLogService.debug( "Cannot deleting file, pathToFile null or empty" );
            return false;
        }

        boolean result = true;

        String completePathToFile = pathToFile.replaceAll( "//", "/" );
        AppLogService.debug( "File to delete " + completePathToFile );
        try
        {
            getS3Client( ).removeObject( RemoveObjectArgs.builder( ).bucket( _s3Bucket ).object( completePathToFile ).build( ) );
        }
        catch ( InvalidKeyException | ErrorResponseException | InsufficientDataException | InternalException | InvalidResponseException | NoSuchAlgorithmException | ServerException
                  | XmlParserException | IllegalArgumentException | IOException | URISyntaxException e )
        {
            result = false;
            AppLogService.error( "Erreur à la supression du fichier " + completePathToFile + " sur NetApp", e );
        }

        AppLogService.debug( "Deleting file " + completePathToFile + " is " + ( result?"OK":"KO" ) );
        return result;
    }
    /**
     * Delete file on NetApp server.
     * @param pathToFile
     *         file to delete, complete with file name (method prepends s3BasePath)
     * @return false if error
     */
    public boolean deleteFileOnNetAppServeurPrependBasePath( String pathToFile )
    {

        if ( StringUtils.isEmpty( pathToFile ) )
        {
            AppLogService.debug( "Cannot deleting file, pathToFile null or empty" );
            return false;
        }

        String completePathToFile = _s3BasePath + pathToFile;

        return deleteFileOnNetAppServeur( completePathToFile );
    }

}
