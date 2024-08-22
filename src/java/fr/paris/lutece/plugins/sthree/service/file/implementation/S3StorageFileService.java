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
import java.net.URISyntaxException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang3.StringUtils;

import com.amazonaws.SdkClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectMetadataRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;

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
    private final Protocol _s3Protocol;

    private final String _s3ProxyDomain;
    private final String _s3ProxyHost;
    private final String _s3ProxyPort;
    private final String _s3ProxyUsername;
    private final String _s3ProxyPassword;
    private final String _s3RequestTimeout;
    private final String _s3ConnectionTimeout;

    private static final String ERROR_400 = "sthree.errormessage.400.badrequest";
    //private static final String ERROR_401 = "sthree.errormessage.401.unauthorized";
    //private static final String ERROR_403 = "sthree.errormessage.403.forbidden";
    private static final String ERROR_404 = "sthree.errormessage.404.filenotfound";
    //private static final String ERROR_408 = "sthree.errormessage.408.timeout";
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
    private AmazonS3 _s3Client;

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
            String s3Key, String s3Password, String s3DefaultFilePath, String s3ProxyDomain, String s3ProxyHost, String s3ProxyPort, String s3ProxyUsername,
            String s3ProxyPassword, String s3RequestTimeout, String s3ConnectionTimeout )
    {
        this._fileDownloadUrlService = _fileDownloadUrlService;
        this._fileRBACService = _fileRBACService;

        _s3Url = s3Url;
        _s3Bucket = s3Bucket;
        _s3Key = s3Key;
        _s3Password = s3Password;
        _s3DefaultFilePath = s3DefaultFilePath;
        _s3Protocol = s3Url.startsWith( "https" ) ? Protocol.HTTPS : Protocol.HTTP;

        _s3ProxyDomain = s3ProxyDomain.isEmpty( ) ? null : s3ProxyDomain;
        _s3ProxyHost = s3ProxyHost.isEmpty( ) ? null : s3ProxyHost;
        _s3ProxyPort = s3ProxyPort.isEmpty( ) ? null : s3ProxyPort;
        _s3ProxyUsername = s3ProxyUsername.isEmpty( ) ? null : s3ProxyUsername;
        _s3ProxyPassword = s3ProxyPassword.isEmpty( ) ? null : s3ProxyPassword;
        _s3RequestTimeout = s3RequestTimeout.isEmpty( ) ? null : s3RequestTimeout;
        _s3ConnectionTimeout = s3ConnectionTimeout.isEmpty( ) ? null : s3ConnectionTimeout;
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

    private AmazonS3 getS3Client( ) throws URISyntaxException
    {
        if ( _s3Client == null )
        {
            AmazonS3ClientBuilder s3ClientBuilder = AmazonS3ClientBuilder.standard( )
                    .withCredentials( new AWSStaticCredentialsProvider( new BasicAWSCredentials( _s3Key, _s3Password ) ) )
                    .withEndpointConfiguration( new EndpointConfiguration( _s3Url, Regions.DEFAULT_REGION.getName( ) ) ).withPathStyleAccessEnabled( true );

            ClientConfiguration clientConfiguration = new ClientConfiguration( );
            clientConfiguration.setProtocol( _s3Protocol );

            if ( _s3ProxyHost != null && _s3ProxyPort != null && _s3ProxyUsername != null && _s3ProxyPassword != null )
            {
                clientConfiguration.setProxyDomain( _s3ProxyDomain );
                clientConfiguration.setProxyHost( _s3ProxyHost );
                clientConfiguration.setProxyPort( Integer.parseInt( _s3ProxyPort ) );
                clientConfiguration.setProxyUsername( _s3ProxyUsername );
                clientConfiguration.setProxyPassword( _s3ProxyPassword );
            }
            if ( _s3RequestTimeout != null )
            {
                clientConfiguration.setRequestTimeout( Integer.parseInt( _s3RequestTimeout ) );
            }
            if ( _s3ConnectionTimeout != null )
            {
                clientConfiguration.setConnectionTimeout( Integer.parseInt( _s3ConnectionTimeout ) );
            }

            s3ClientBuilder.withClientConfiguration( clientConfiguration );
            AmazonS3 s3Client = s3ClientBuilder.build( );

            setS3Client( s3Client );
        }

        return _s3Client;
    }

    private void setS3Client( AmazonS3 s3Client )
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
                getS3Client( ).deleteObject( _s3Bucket, strPath );
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
                // get meta data
                Map<String, String> metadata = getS3Client( ).getObjectMetadata( new GetObjectMetadataRequest( _s3Bucket, strPath ) ).getUserMetadata( );
                if ( metadata == null )
                    return null;
                else
                {
                    File file = new File( );
                    file.setFileKey( strPath );
                    file.setMimeType( metadata.get( METADATA_MIME_TYPE ) );
                    file.setSize( Integer.parseInt( metadata.get( METADATA_SIZE ) ) );
                    file.setTitle( metadata.get( METADATA_TITLE ) );
                    file.setOrigin( metadata.get( METADATA_ORIGIN ) );

                    if ( withPhysicalFile )
                    {
                        // get file content
                        file.setPhysicalFile( new PhysicalFile( ) );
                        file.getPhysicalFile( )
                                .setValue( getS3Client( ).getObject( new GetObjectRequest( _s3Bucket, strPath ) ).getObjectContent( ).readAllBytes( ) );
                    }

                    return file;
                }
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
            ObjectMetadata metadata = new ObjectMetadata( );
            metadata.addUserMetadata( METADATA_ORIGIN, getName( ) );
            metadata.addUserMetadata( METADATA_MIME_TYPE, "application/octet-stream" );

            getS3Client( ).putObject( _s3Bucket, strPath, new ByteArrayInputStream( blob ), metadata );
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
            ObjectMetadata metadata = new ObjectMetadata( );
            metadata.addUserMetadata( METADATA_ORIGIN, getName( ) );
            metadata.addUserMetadata( METADATA_MIME_TYPE, "application/octet-stream" );

            getS3Client( ).putObject( _s3Bucket, strPath, inputStream, metadata );
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
    public String storeFileItem( FileItem fileItem ) throws FileServiceException
    {
        // Should be parametrized
        String strPath = S3Util.getStrPath( _s3DefaultFilePath );
        try
        {
            ObjectMetadata metadata = new ObjectMetadata( );
            metadata.addUserMetadata( METADATA_TITLE, fileItem.getName( ) );
            metadata.addUserMetadata( METADATA_SIZE, String.valueOf( fileItem.getSize( ) ) );
            metadata.addUserMetadata( METADATA_ORIGIN, getName( ) );
            metadata.addUserMetadata( METADATA_MIME_TYPE, fileItem.getContentType( ) );

            getS3Client( ).putObject( _s3Bucket, strPath, fileItem.getInputStream( ), metadata );
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
        catch( SdkClientException e )
        {
            AppLogService.error( "Erreur de sauvegarde du fichier " + strPath, e );
            logAWSException( e );
            throw new FileServiceException( ERROR_503, 503, e );
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
            ObjectMetadata metadata = new ObjectMetadata( );
            metadata.addUserMetadata( METADATA_TITLE, file.getTitle( ) );
            metadata.addUserMetadata( METADATA_SIZE, String.valueOf( file.getSize( ) ) );
            metadata.addUserMetadata( METADATA_ORIGIN, getName( ) );
            metadata.addUserMetadata( METADATA_MIME_TYPE, file.getMimeType( ) );

            getS3Client( ).putObject( _s3Bucket, strPath, new ByteArrayInputStream( file.getPhysicalFile( ).getValue( ) ), metadata );
        }
        catch( URISyntaxException e )
        {
            AppLogService.error( "Erreur chargement des métadonnées " + strPath, e );
            throw new FileServiceException( ERROR_400, 400, e );
        }
        catch( SdkClientException e )
        {
            AppLogService.error( "Erreur de sauvegarde du fichier " + strPath, e );
            logAWSException( e );
            throw new FileServiceException( ERROR_503, 503, e );
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
        checkAccessRights( fileData, AdminAuthenticationService.getInstance( ).getRegisteredUser( request ) );

        // check validity
        checkLinkValidity( fileData );

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
}
