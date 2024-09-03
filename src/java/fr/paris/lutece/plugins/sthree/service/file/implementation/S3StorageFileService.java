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

package fr.paris.lutece.plugins.sthree.service.file.implementation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang3.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.model.ChecksumAlgorithm;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.http.urlconnection.ProxyConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.Builder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectAttributesRequest;
import software.amazon.awssdk.services.s3.model.GetObjectAttributesResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectAttributes;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import fr.paris.lutece.api.user.User;
import fr.paris.lutece.plugins.sthree.util.S3Util;
import fr.paris.lutece.portal.business.file.File;
import fr.paris.lutece.portal.business.physicalfile.PhysicalFile;
import fr.paris.lutece.portal.service.admin.AccessDeniedException;
import fr.paris.lutece.portal.service.admin.AdminAuthenticationService;
import fr.paris.lutece.portal.service.file.ExpiredLinkException;
import fr.paris.lutece.portal.service.file.FileService;
import fr.paris.lutece.portal.service.file.IFileDownloadUrlService;
import fr.paris.lutece.portal.service.file.IFileRBACService;
import fr.paris.lutece.portal.service.file.IFileStoreServiceProvider;
import fr.paris.lutece.portal.service.security.SecurityService;
import fr.paris.lutece.portal.service.security.UserNotSignedException;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.file.FileServiceException;

/**
 * 
 * S3StorageFileService.
 * 
 */
public class S3StorageFileService implements IFileStoreServiceProvider
{
    private static final long serialVersionUID = 1L;

    // Properties
    private final String _s3Url;
    private final String _s3Bucket;
    private final String _s3Key;
    private final String _s3Password;
    private final String _s3DefaultFilePath;

    private boolean _s3ForcePathStyle;
    private Region _s3Region;
    private ChecksumAlgorithm _s3ChecksumAlgorithm;

    private final String _s3ProxyHost;
    private final String _s3ProxyUsername;
    private final String _s3ProxyPassword;

    private final long _s3RequestTimeout;
    private final long _s3ConnectionTimeout;

    private static final String ERROR_400 = "sthree.errormessage.400.badrequest";
    private static final String ERROR_401 = "sthree.errormessage.401.unauthorized";
    private static final String ERROR_403 = "sthree.errormessage.403.forbidden";
    private static final String ERROR_404 = "sthree.errormessage.404.filenotfound";
    private static final String ERROR_408 = "sthree.errormessage.408.timeout";
    private static final String ERROR_500 = "sthree.errormessage.500.internalservererror";
    private static final String ERROR_503 = "sthree.errormessage.503.serviceunavailable";

    // Constants
    private static final String METADATA_MIME_TYPE = "mimeType";
    private static final String METADATA_SIZE = "size";
    private static final String METADATA_TITLE = "title";
    private static final String METADATA_ORIGIN = "origin";

    // Services
    private IFileDownloadUrlService _fileDownloadUrlService;
    private IFileRBACService _fileRBACService;

    // AWS
    private S3Client _s3Client;

    // Attributes
    private String _strName;
    private boolean _bDefault;

    /**
     * init
     * 
     * @param _fileDownloadUrlService
     * @param _fileRBACService
     * @param s3Url
     *            : the URL of the S3 server
     * @param s3Bucket
     *            : the name of the S3 bucket
     * @param s3Key
     *            : the key to access the S3 bucket
     * @param s3Password
     *            : the password to access the S3 bucket
     */
    public S3StorageFileService( IFileDownloadUrlService _fileDownloadUrlService, IFileRBACService _fileRBACService, String s3Url, String s3Bucket,
            String s3Key, String s3Password, String s3DefaultFilePath, String s3ForcePathStyle, String s3Region, String s3ChecksumAlgorithm, String s3ProxyHost, String s3ProxyUsername,
            String s3ProxyPassword, String s3RequestTimeout, String s3ConnectionTimeout )
    {
        this._fileDownloadUrlService = _fileDownloadUrlService;
        this._fileRBACService = _fileRBACService;

        this._s3Url = s3Url;
        this._s3Bucket = s3Bucket;
        this._s3Key = s3Key;
        this._s3Password = s3Password;
        this._s3DefaultFilePath = s3DefaultFilePath;

        this._s3ForcePathStyle = s3ForcePathStyle.isEmpty() ? true : Boolean.parseBoolean( "s3ForcePathStyle" );
        this._s3Region = s3Region.isEmpty() ? Region.AWS_GLOBAL : Region.of( s3Region.toUpperCase( ) );
        this._s3ChecksumAlgorithm = s3ChecksumAlgorithm.isEmpty() ? ChecksumAlgorithm.CRC32 : ChecksumAlgorithm.valueOf( s3ChecksumAlgorithm );

        this._s3ProxyHost = s3ProxyHost.isEmpty( ) ? "" : s3ProxyHost;
        this._s3ProxyUsername = s3ProxyUsername.isEmpty( ) ? "" : s3ProxyUsername;
        this._s3ProxyPassword = s3ProxyPassword.isEmpty( ) ? "" : s3ProxyPassword;

        this._s3RequestTimeout = s3RequestTimeout.isEmpty( ) ? 0 : Long.parseLong( s3RequestTimeout );
        this._s3ConnectionTimeout = s3ConnectionTimeout.isEmpty( ) ? 0 : Long.parseLong( s3ConnectionTimeout );
    }

    /**
     * get the FileRBACService
     * 
     * @return the FileRBACService
     */
    public IFileRBACService getFileRBACService( )
    {
        return _fileRBACService;
    }

    /**
     * set the FileRBACService
     * 
     * @param fileRBACService
     */
    public void setFileRBACService( IFileRBACService fileRBACService )
    {
        this._fileRBACService = fileRBACService;
    }

    /**
     * Get the downloadService
     * 
     * @return the downloadService
     */
    public IFileDownloadUrlService getDownloadUrlService( )
    {
        return _fileDownloadUrlService;
    }

    /**
     * Sets the downloadService
     * 
     * @param downloadUrlService
     *            downloadService
     */
    public void setDownloadUrlService( IFileDownloadUrlService downloadUrlService )
    {
        _fileDownloadUrlService = downloadUrlService;
    }

    private S3Client getS3Client( ) throws URISyntaxException
    {
        if ( _s3Client == null )
        {

            AwsBasicCredentials credentials = AwsBasicCredentials.create( _s3Key, _s3Password );
            StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create( credentials );

            Builder httpClient = UrlConnectionHttpClient.builder();

            S3ClientBuilder s3ClientBuilder = S3Client.builder()
            .credentialsProvider( credentialsProvider )
            .endpointOverride( new URI( _s3Url ) )
            .forcePathStyle( _s3ForcePathStyle )
            .region( _s3Region );

            if ( !_s3ProxyHost.isEmpty() && !_s3ProxyUsername.isEmpty() && !_s3ProxyPassword.isEmpty())
            {
                ProxyConfiguration proxy = ProxyConfiguration.builder( )
                .endpoint( new URI( _s3ProxyHost ) )
                .username( _s3ProxyUsername )
                .password( _s3ProxyPassword )
                .build( );

                httpClient.proxyConfiguration( proxy );

                s3ClientBuilder.httpClientBuilder( httpClient );
            }
            if ( _s3RequestTimeout != 0 )
            {
                //httpClient.socketTimeout(Duration.ofSeconds( _s3RequestTimeout ));
                s3ClientBuilder.overrideConfiguration(b -> b.apiCallAttemptTimeout( Duration.ofSeconds( _s3RequestTimeout )));
            }
            if ( _s3ConnectionTimeout != 0 )
            {
                //httpClient.connectionTimeout(Duration.ofSeconds( _s3ConnectionTimeout ));
                s3ClientBuilder.overrideConfiguration(b -> b.apiCallTimeout( Duration.ofSeconds( _s3ConnectionTimeout )));
            }

            setS3Client( s3ClientBuilder.build() );
        }

        return _s3Client;
    }

    private void setS3Client( S3Client s3Client )
    {
        _s3Client = s3Client;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName( )
    {
        return _strName;
    }

    /**
     * {@inheritDoc}
     * 
     * @throws FileServiceException
     */
    @Override
    public void delete( String strPath ) throws FileServiceException
    {
        if ( StringUtils.isEmpty( strPath ) )
        {
            AppLogService.debug( "Cannot delete file, pathToFile null or empty" );
            return;
        }
        else
        {
            try
            {
                getS3Client( ).deleteObject(DeleteObjectRequest.builder().bucket(_s3Bucket).key(strPath).build());
            }
            catch( URISyntaxException e )
            {
                AppLogService.error( "Erreur à la supression du fichier " + strPath, e );
                throw new FileServiceException( ERROR_400, 400, e );
            }
            catch( SdkClientException e )
            {
                logAWSException( e );
                throw new FileServiceException( ERROR_503, 503, e );
            }
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @throws FileServiceException
     */
    @Override
    public File getFile( String strPath ) throws FileServiceException
    {
        return getFile( strPath, true );
    }

    /**
     * {@inheritDoc}
     * 
     * @throws FileServiceException
     */
    @Override
    public File getFileMetaData( String strPath ) throws FileServiceException
    {
        return getFile( strPath, false );
    }

    /**
     * get file from database
     * 
     * @param strKey
     * @param withPhysicalFile
     * 
     * @return the file with the physical file content if withPhysicalFile is true, null otherwise
     * @throws FileServiceException
     */
    public File getFile( String strPath, boolean withPhysicalFile ) throws FileServiceException
    {
        if ( StringUtils.isNotBlank( strPath ) )
        {
            try
            {
                Map<String, String> metadata = getS3Client( ).headObject( 
                    HeadObjectRequest.builder()
                    .bucket( _s3Bucket )
                    .key( strPath )
                    .checksumMode( ChecksumMode.ENABLED )
                    .build() ).metadata( );

                if( metadata.isEmpty( ) )
                    return null;
                else
                {
                    File file = new File( );
                    file.setFileKey( strPath );
                    file.setMimeType( metadata.getOrDefault( METADATA_MIME_TYPE, "") );
                    file.setSize( Integer.parseInt(metadata.getOrDefault( METADATA_SIZE, "0" ) ) );
                    file.setTitle( metadata.getOrDefault( METADATA_TITLE, "" ) );
                    file.setOrigin( metadata.getOrDefault( METADATA_ORIGIN, "" ) );

                    if ( withPhysicalFile )
                    {
                        // get file content
                        file.setPhysicalFile( new PhysicalFile( ) );
                        file.getPhysicalFile( )
                                .setValue( getS3Client( ).getObject( 
                                    GetObjectRequest.builder()
                                    .checksumMode( ChecksumMode.ENABLED )
                                    .bucket( _s3Bucket )
                                    .key( strPath )
                                    .build() ).readAllBytes( ) );
                    }

                    return file;
                }
            }
            catch (S3Exception e) {
                AppLogService.error( "Erreur chargement des métadonnées " + strPath, e );
                throw new FileServiceException( getExceptionByStatusCode( e.statusCode() ), e.statusCode(), e );
            }
            catch( URISyntaxException e )
            {
                AppLogService.error( "Erreur chargement des métadonnées " + strPath, e );
                throw new FileServiceException( ERROR_400, 400, e );
            }
            catch( IOException e )
            {
                AppLogService.error( "Erreur chargement des métadonnées " + strPath, e );
                throw new FileServiceException( ERROR_404, 404, e );
            }
        }
        else
            return null;
    }

    /**
     * {@inheritDoc}
     * 
     * @throws FileServiceException
     */
    @Override
    public String storeBytes( byte [ ] blob ) throws FileServiceException
    {
        String strPath = S3Util.getStrPath( _s3DefaultFilePath );
        try
        {
            Map<String,String> metadata = new HashMap<String,String>();
            metadata.put( METADATA_ORIGIN, getName( ) );
            metadata.put( METADATA_MIME_TYPE, "application/octet-stream" );

            getS3Client( ).putObject( 
                PutObjectRequest.builder()
                .bucket(_s3Bucket)
                .key(strPath)
                .metadata(metadata)
                .checksumAlgorithm(_s3ChecksumAlgorithm)
                .build(), 
                RequestBody.fromBytes(blob) 
            );
        }
        catch (S3Exception e) {
            AppLogService.error( "Erreur chargement des métadonnées " + strPath, e.awsErrorDetails().errorCode() );
            throw new FileServiceException( getExceptionByStatusCode( e.statusCode() ), e.statusCode(), e );
        }
        catch( SdkClientException e )
        {
            AppLogService.error( "Erreur chargement des métadonnées " + strPath, e );
            throw new FileServiceException( ERROR_503, 503, e );
        }
        catch( URISyntaxException e )
        {
            AppLogService.error( "Erreur chargement des métadonnées " + strPath, e );
            throw new FileServiceException( ERROR_400, 400, e );
        }
        return strPath;
    }

    /**
     * {@inheritDoc}
     * 
     * @throws FileServiceException
     */
    @Override
    public String storeInputStream( InputStream inputStream ) throws FileServiceException
    {
        String strPath = S3Util.getStrPath( _s3DefaultFilePath );
        try
        {
            Map<String,String> metadata = new HashMap<String,String>();
            metadata.put( METADATA_ORIGIN, getName( ) );
            metadata.put( METADATA_MIME_TYPE, "application/octet-stream" );

            getS3Client( ).putObject( 
                PutObjectRequest.builder()
                .bucket(_s3Bucket)
                .key(strPath)
                .metadata(metadata)
                .checksumAlgorithm(_s3ChecksumAlgorithm)
                .build(), 
                RequestBody.fromInputStream(inputStream, inputStream.available( ) ) 
                );
        }
        catch ( S3Exception e) {
            AppLogService.error( "Erreur chargement des métadonnées " + strPath, e );
            throw new FileServiceException( getExceptionByStatusCode( e.statusCode() ), e.statusCode(), e );
        }
        catch( IOException e )
        {
            AppLogService.error( "Erreur chargement des métadonnées " + strPath, e );
            throw new FileServiceException( ERROR_408, 408, e );
        }
        catch( URISyntaxException e )
        {
            AppLogService.error( "Erreur chargement des métadonnées " + strPath, e );
            throw new FileServiceException( ERROR_400, 400, e );
        }

        return strPath;
    }

    /**
     * {@inheritDoc}
     * 
     * @throws FileServiceException
     */
    @Override
    public String storeFileItem( FileItem fileItem ) throws FileServiceException
    {
        String strPath = S3Util.getStrPath( _s3DefaultFilePath );
        try
        {
            Map<String,String> metadata = new HashMap<String,String>();
            metadata.put( METADATA_TITLE, fileItem.getName( ) );
            metadata.put( METADATA_SIZE, String.valueOf( fileItem.getSize( ) ) );
            metadata.put( METADATA_ORIGIN, getName( ) );
            metadata.put( METADATA_MIME_TYPE, fileItem.getContentType( ) );

            getS3Client( ).putObject( 
                PutObjectRequest.builder()
                .bucket(_s3Bucket)
                .key(strPath)
                .metadata(metadata)
                .checksumAlgorithm(_s3ChecksumAlgorithm)
                .build(), 
                RequestBody.fromInputStream(fileItem.getInputStream(), fileItem.getSize( ) ) 
                );
        }
        catch( IOException e )
        {
            AppLogService.error( "Erreur chargement des métadonnées " + strPath, e );
            throw new FileServiceException( ERROR_404, 404, e );
        }
        catch( URISyntaxException e )
        {
            AppLogService.error( "Erreur chargement des métadonnées " + strPath, e );
            throw new FileServiceException( ERROR_400, 400, e );
        }
        catch ( S3Exception e) {
            AppLogService.error( "Erreur chargement des métadonnées " + strPath, e );
            throw new FileServiceException( getExceptionByStatusCode( e.statusCode() ), e.statusCode(), e );
        }

        return strPath;
    }

    /**
     * {@inheritDoc}
     * 
     * @throws FileServiceException
     */
    @Override
    public String storeFile( File file ) throws FileServiceException
    {
        String strPath = S3Util.getStrPath( _s3DefaultFilePath );
        try
        {
            Map<String,String> metadata = new HashMap<String,String>();
            metadata.put( METADATA_TITLE, file.getTitle( ) );
            metadata.put( METADATA_SIZE, String.valueOf( file.getSize( ) ) );
            metadata.put( METADATA_ORIGIN, getName( ) );
            metadata.put( METADATA_MIME_TYPE, file.getMimeType( ) );

            getS3Client( ).putObject( 
                PutObjectRequest.builder()
                .bucket(_s3Bucket)
                .key(strPath)
                .metadata(metadata)
                .checksumAlgorithm(_s3ChecksumAlgorithm)
                .build(), 
                RequestBody.fromBytes( file.getPhysicalFile( ).getValue( ) ) 
                );
        }
        catch( URISyntaxException e )
        {
            AppLogService.error( "Erreur chargement des métadonnées " + strPath, e );
            throw new FileServiceException( ERROR_400, 400, e );
        }
        catch (S3Exception e) {
            AppLogService.error( "Erreur chargement des métadonnées " + strPath, e.awsErrorDetails().errorCode() );
            throw new FileServiceException( getExceptionByStatusCode( e.statusCode() ), e.statusCode(), e );
        }

        return strPath;
    }

    public void setDefault( boolean bDefault )
    {
        this._bDefault = bDefault;
    }

    public void setName( String strName )
    {
        _strName = strName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDefault( )
    {
        return _bDefault;
    }

    /**
     * {@inheritDoc}
     * 
     * @throws FileServiceException
     */
    @Override
    public InputStream getInputStream( String strKey ) throws FileServiceException
    {

        File file = getFile( strKey );

        return new ByteArrayInputStream( file.getPhysicalFile( ).getValue( ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFileDownloadUrlFO( String strKey )
    {
        return _fileDownloadUrlService.getFileDownloadUrlFO( strKey, getName( ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFileDownloadUrlFO( String strKey, Map<String, String> additionnalData )
    {
        return _fileDownloadUrlService.getFileDownloadUrlFO( strKey, additionnalData, getName( ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFileDownloadUrlBO( String strKey )
    {
        return _fileDownloadUrlService.getFileDownloadUrlBO( strKey, getName( ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFileDownloadUrlBO( String strKey, Map<String, String> additionnalData )
    {
        return _fileDownloadUrlService.getFileDownloadUrlBO( strKey, additionnalData, getName( ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkAccessRights( Map<String, String> fileData, User user ) throws AccessDeniedException, UserNotSignedException
    {
        if ( _fileRBACService != null )
        {
            _fileRBACService.checkAccessRights( fileData, user );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkLinkValidity( Map<String, String> fileData ) throws ExpiredLinkException
    {
        _fileDownloadUrlService.checkLinkValidity( fileData );
    }

    /**
     * {@inheritDoc}
     * 
     * @throws FileServiceException
     */
    @Override
    public File getFileFromRequestBO( HttpServletRequest request )
            throws AccessDeniedException, ExpiredLinkException, UserNotSignedException, FileServiceException
    {
        Map<String, String> fileData = _fileDownloadUrlService.getRequestDataBO( request );

        // check access rights
        //checkAccessRights( fileData, AdminAuthenticationService.getInstance( ).getRegisteredUser( request ) );

        // check validity
        //checkLinkValidity( fileData );

        // The path to the ressource should

        String strFileId = fileData.get( FileService.PARAMETER_FILE_ID );

        return getFile( strFileId );
    }

    /**
     * {@inheritDoc}
     * 
     * @throws FileServiceException
     */
    @Override
    public File getFileFromRequestFO( HttpServletRequest request )
            throws AccessDeniedException, ExpiredLinkException, UserNotSignedException, FileServiceException
    {

        Map<String, String> fileData = _fileDownloadUrlService.getRequestDataFO( request );

        // check access rights
        checkAccessRights( fileData, SecurityService.getInstance( ).getRegisteredUser( request ) );

        // check validity
        checkLinkValidity( fileData );

        String strFileId = fileData.get( FileService.PARAMETER_FILE_ID );

        return getFile( strFileId );
    }

    public boolean healthCheck( )
    {
        try
        {
            getS3Client( ).listBuckets( );
            return true;
        }
        catch( SdkClientException | URISyntaxException e )
        {
            logAWSException( e );
            return false;
        }
    }

    private void logAWSException( Exception e )
    {
        if ( e.getMessage( ) != null )
        {
            AppLogService.debug( "Message \n" + e.getMessage( ) );
        }
        if ( e.getCause( ) != null )
        {
            AppLogService.debug( "Cause \n" + e.getCause( ) );
        }
    }

    private String getExceptionByStatusCode ( int statusCode ) 
    {
        switch( statusCode )
        {
            case 400:
               return ERROR_400;
            case 401:
                return ERROR_401;
            case 403:
                return ERROR_403;
            case 404:
                return ERROR_404;
            case 408:
                return ERROR_408;
            case 500:
                return ERROR_500;
            case 503:
                return ERROR_503;
            default:
                return "Error " + statusCode;
        }
    } 

}
