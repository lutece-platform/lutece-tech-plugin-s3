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

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.s3.model.ChecksumAlgorithm;

import fr.paris.lutece.portal.service.file.FileService;
import fr.paris.lutece.portal.service.file.IFileStoreServiceProvider;
import fr.paris.lutece.portal.service.file.implementation.DefaultFileDownloadService;
import fr.paris.lutece.test.LuteceTestCase;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Tests the CDI wiring of the S3 file store service provider.
 */
public class S3StorageFileServiceTest extends LuteceTestCase
{
    private static final String PROVIDER_NAME = "s3StorageFileService";

    @Inject
    @Named( S3StorageFileServiceProducer.BEAN_FILE_STORE_SERVICE_PROVIDER )
    private IFileStoreServiceProvider _provider;

    @Inject
    private FileService _fileService;

    /**
     * Checks the produced provider keeps its v7 name and is not the default one.
     */
    @Test
    public void testProducedProvider( )
    {
        assertEquals( PROVIDER_NAME, _provider.getName( ) );
        assertFalse( _provider.isDefault( ) );
    }

    /**
     * Checks the file service finds the provider by its name.
     */
    @Test
    public void testLookupByName( )
    {
        assertEquals( PROVIDER_NAME, _fileService.getFileStoreServiceProvider( PROVIDER_NAME ).getName( ) );
        assertNotEquals( PROVIDER_NAME, _fileService.getFileStoreServiceProvider( ).getName( ) );
    }

    /**
     * Checks a blank key reads nothing and calls no S3 server.
     *
     * @throws Exception
     *             if the provider fails
     */
    @Test
    public void testBlankKey( ) throws Exception
    {
        assertNull( _provider.getFile( "" ) );
        assertNull( _provider.getFileMetaData( null ) );
        _provider.delete( "" );
    }

    /**
     * Checks the download URL service uses the separator that allows paths in file keys.
     */
    @Test
    public void testDownloadSeparator( )
    {
        DefaultFileDownloadService downloadService = (DefaultFileDownloadService) new S3StorageFileServiceProducer( ).produceFileDownloadUrlService( );

        assertEquals( "!", downloadService.getSeparator( ) );
    }

    /**
     * Checks every documented checksum value is accepted and an unknown one falls back to CRC32.
     */
    @Test
    public void testChecksumAlgorithm( )
    {
        assertEquals( ChecksumAlgorithm.CRC32, S3StorageFileService.parseChecksumAlgorithm( "" ) );
        assertEquals( ChecksumAlgorithm.CRC32_C, S3StorageFileService.parseChecksumAlgorithm( "CRC32C" ) );
        assertEquals( ChecksumAlgorithm.SHA1, S3StorageFileService.parseChecksumAlgorithm( "sha1" ) );
        assertEquals( ChecksumAlgorithm.SHA256, S3StorageFileService.parseChecksumAlgorithm( "SHA256" ) );
        assertNull( S3StorageFileService.parseChecksumAlgorithm( "DISABLED" ) );
        assertEquals( ChecksumAlgorithm.CRC32, S3StorageFileService.parseChecksumAlgorithm( "MD5" ) );
    }
}
