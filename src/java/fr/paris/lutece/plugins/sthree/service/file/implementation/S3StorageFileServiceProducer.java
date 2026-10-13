/*
 * Copyright (c) 2002-2026, City of Paris
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

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import fr.paris.lutece.portal.service.file.IFileDownloadUrlService;
import fr.paris.lutece.portal.service.file.IFileRBACService;
import fr.paris.lutece.portal.service.file.IFileStoreServiceProvider;
import fr.paris.lutece.portal.service.file.implementation.DefaultFileDownloadService;
import fr.paris.lutece.portal.service.util.CdiHelper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

/**
 * Produces the S3 file store service provider and its download URL service.
 */
@ApplicationScoped
public class S3StorageFileServiceProducer
{
    public static final String BEAN_FILE_STORE_SERVICE_PROVIDER = "sthree.s3StorageFileService";
    public static final String BEAN_FILE_DOWNLOAD_URL_SERVICE = "sthree.s3FileDownloadUrlService";

    private static final String PROVIDER_NAME = "s3StorageFileService";
    private static final String DOWNLOAD_SEPARATOR = "!";

    /**
     * Produces a download URL service whose separator allows paths in file keys.
     *
     * @return the download URL service
     */
    @Produces
    @ApplicationScoped
    @Named( BEAN_FILE_DOWNLOAD_URL_SERVICE )
    public IFileDownloadUrlService produceFileDownloadUrlService( )
    {
        DefaultFileDownloadService downloadService = new DefaultFileDownloadService( );
        downloadService.setSeparator( DOWNLOAD_SEPARATOR );

        return downloadService;
    }

    /**
     * Produces the S3 file store service provider from the plugin configuration.
     *
     * @param strDownloadServiceName
     *            the bean name of the download URL service
     * @param strRbacServiceName
     *            the bean name of the RBAC service
     * @param strUrl
     *            the S3 server URL
     * @param strBucket
     *            the S3 bucket
     * @param strKey
     *            the S3 access key
     * @param strPassword
     *            the S3 secret key
     * @param strDefaultFilePath
     *            the file path pattern
     * @param strForcePathStyle
     *            the path style flag
     * @param strRegion
     *            the S3 region
     * @param strChecksumAlgorithm
     *            the checksum algorithm
     * @param strProxyHost
     *            the proxy host
     * @param strProxyUsername
     *            the proxy user name
     * @param strProxyPassword
     *            the proxy password
     * @param strRequestTimeout
     *            the request timeout in seconds
     * @param strConnectionTimeout
     *            the connection timeout in seconds
     * @return the S3 file store service provider
     */
    @Produces
    @ApplicationScoped
    @Named( BEAN_FILE_STORE_SERVICE_PROVIDER )
    public IFileStoreServiceProvider produceS3StorageFileService(
            @ConfigProperty( name = "sthree.s3StorageFileService.downloadService", defaultValue = BEAN_FILE_DOWNLOAD_URL_SERVICE ) String strDownloadServiceName,
            @ConfigProperty( name = "sthree.s3StorageFileService.rbacService", defaultValue = "defaultFileRBACService" ) String strRbacServiceName,
            @ConfigProperty( name = "s3Url" ) Optional<String> strUrl, @ConfigProperty( name = "s3Bucket" ) Optional<String> strBucket,
            @ConfigProperty( name = "s3Key" ) Optional<String> strKey, @ConfigProperty( name = "s3Password" ) Optional<String> strPassword,
            @ConfigProperty( name = "s3DefaultFilePath" ) Optional<String> strDefaultFilePath,
            @ConfigProperty( name = "s3ForcePathStyle" ) Optional<String> strForcePathStyle, @ConfigProperty( name = "s3Region" ) Optional<String> strRegion,
            @ConfigProperty( name = "s3ChecksumAlgorithm" ) Optional<String> strChecksumAlgorithm,
            @ConfigProperty( name = "s3ProxyHost" ) Optional<String> strProxyHost, @ConfigProperty( name = "s3ProxyUsername" ) Optional<String> strProxyUsername,
            @ConfigProperty( name = "s3ProxyPassword" ) Optional<String> strProxyPassword,
            @ConfigProperty( name = "s3RequestTimeout" ) Optional<String> strRequestTimeout,
            @ConfigProperty( name = "s3ConnectionTimeout" ) Optional<String> strConnectionTimeout )
    {
        S3StorageFileService service = new S3StorageFileService( CdiHelper.getReference( IFileDownloadUrlService.class, strDownloadServiceName ),
                CdiHelper.getReference( IFileRBACService.class, strRbacServiceName ), strUrl.orElse( "" ), strBucket.orElse( "" ), strKey.orElse( "" ),
                strPassword.orElse( "" ), strDefaultFilePath.orElse( "" ), strForcePathStyle.orElse( "" ), strRegion.orElse( "" ),
                strChecksumAlgorithm.orElse( "" ), strProxyHost.orElse( "" ), strProxyUsername.orElse( "" ), strProxyPassword.orElse( "" ),
                strRequestTimeout.orElse( "" ), strConnectionTimeout.orElse( "" ) );
        service.setName( PROVIDER_NAME );

        return service;
    }
}
