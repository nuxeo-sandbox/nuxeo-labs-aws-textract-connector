/*
 * (C) Copyright 2025 Hyland (http://hyland.com/)  and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thibaud Arguillere
 */
package org.nuxeo.labs.aws.textract;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CloseableFile;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.KeyStrategy;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;

import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.AmazonTextractClientBuilder;
import com.amazonaws.services.textract.model.AnalyzeDocumentRequest;
import com.amazonaws.services.textract.model.AnalyzeDocumentResult;
import com.amazonaws.services.textract.model.DetectDocumentTextRequest;
import com.amazonaws.services.textract.model.DetectDocumentTextResult;
import com.amazonaws.services.textract.model.Document;
import com.amazonaws.services.textract.model.S3Object;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Note really a service in terms of Nuxeo Service for now, because we had to develop this very quickly for a demo.
 */
public class TextractService {

    protected String bucket;

    protected String bucketPrefix;

    protected String region;

    protected AmazonTextract textractClient;

    protected static TextractService instance = null;

    protected static int checkS3BlobProviderClass = -1;

    // This is for unit tests only
    protected boolean forceS3Key = false;

    List<String> DEFAULT_ANALYZE_FEATURES = List.of("TABLES", "FORMS");

    public static TextractService getInstance(String bucket, String bucketPrefix, String region) {
        if (instance == null) {
            synchronized (TextractService.class) {
                if (instance == null) {
                    instance = new TextractService(bucket, bucketPrefix, region);
                }
            }
        }
        return instance;
    }

    public void setForceS3Key(boolean value) {
        forceS3Key = value;
    }

    public static TextractService getInstance() {
        if (instance == null) {
            synchronized (TextractService.class) {
                if (instance == null) {
                    instance = new TextractService();
                }
            }
        }
        return instance;
    }

    private TextractService(String bucket, String bucketPrefix, String region) {

        this.bucket = bucket;

        this.bucketPrefix = bucketPrefix;
        if (StringUtils.isNotBlank(bucketPrefix)) {
            if (!bucketPrefix.endsWith("/")) {
                this.bucketPrefix += "/";
            }
        } else {
            this.bucketPrefix = "binary_store/";
        }

        this.region = region;

        EndpointConfiguration endpoint = new EndpointConfiguration("https://textract." + region + ".amazonaws.com",
                region);
        textractClient = AmazonTextractClientBuilder.standard().withEndpointConfiguration(endpoint).build();
    }

    private TextractService() {
        /*
         * nuxeo.s3storage.bucket=eu-west-1-demo-bucket
         * nuxeo.s3storage.bucket_prefix=CIC-POC-EDF/binary_store/
         * nuxeo.s3storage.region=eu-west-1
         */
        this(Framework.getProperty("nuxeo.s3storage.bucket"), Framework.getProperty("nuxeo.s3storage.bucket_prefix"),
                Framework.getProperty("nuxeo.s3storage.region"));

    }

    // ========================================> Analyze
    /**
     * WARNING: assumes the blob is on S3
     * 
     * @param features
     * @param blobJKeyOnS3
     * @return
     * @since TODO
     */
    public AnalyzeDocumentResult analyze(List<String> features, String blobKeyOnS3) {

        if (features == null || features.size() == 0) {
            features = DEFAULT_ANALYZE_FEATURES;
        }

        String s3Path = bucketPrefix + blobKeyOnS3;
        AnalyzeDocumentRequest request = new AnalyzeDocumentRequest().withFeatureTypes(features.toArray(new String[0]))
                                                                     .withDocument(new Document().withS3Object(
                                                                             new S3Object().withName(s3Path)
                                                                                           .withBucket(bucket)));

        AnalyzeDocumentResult result = textractClient.analyzeDocument(request);

        return result;

    }

    /*
     * Return null if this is not a S3 blob
     * The proper way to detect a blob is on S3 is:
     * - Check that it's a ManagedBlob first, and also that its BlobProvider's BlobStore, once unwrapped is an
     * S3BlobStore
     * - Use ManagedBlob.getKey, not getDigest
     * - Call stripBlobKeyPrefix and stripBlobKeyVersionSuffix on the blob key too
     * - Call config.bucketKey(key) to add back the bucket prefix and deal with subdirs
     * BUT we don't want to force dthe deployment of a S3BlobProvider etc. if the Nuxeo server runs in another
     * environment and we want this plugin to be generic. So we do it a different, less 100% certain way
     */
    // Server may not have the S3BlobProvider class and we don't want to force
    // deploy it, so we test
    protected boolean hasS3BlobProviderClass() {
        if (checkS3BlobProviderClass == -1) {
            try {
                @SuppressWarnings("unused")
                Class<?> theClass = Class.forName("org.nuxeo.ecm.blob.s3.S3BlobProvider");
                // If we are here, there is a S3BlobProvider class deployed
                checkS3BlobProviderClass = 1;
            } catch (ClassNotFoundException e) {
                checkS3BlobProviderClass = 0;
            }
        }

        return checkS3BlobProviderClass == 1;
    }

    protected String getS3BlobKey(Blob blob) {

        if (blob instanceof ManagedBlob && (hasS3BlobProviderClass() || forceS3Key)) {

            ManagedBlob managedBlob = (ManagedBlob) blob;
            String key = managedBlob.getKey();
            // Strip prefix, if any (should have one for s3)
            int colon = key.indexOf(':');
            if (colon >= 0) {
                key = key.substring(colon + 1);
            }
            // Strip version if any
            int seppos = key.indexOf(KeyStrategy.VER_SEP);
            if (seppos >= 0) {
                key = key.substring(0, seppos);
            }

            return key;
        }

        return null;
    }

    /**
     * TODO If the blob is on S3, use the call telling Textract to get it directly there.
     * Else, send it as BinaryBuffer
     * 
     * @param features
     * @param blob
     * @return
     * @since TODO
     */
    public AnalyzeDocumentResult analyze(List<String> features, Blob blob) {

        // If S3, use it directly
        String s3BlobKey = getS3BlobKey(blob);
        if (StringUtils.isNotBlank(s3BlobKey)) {
            return analyze(features, s3BlobKey);
        }

        if (features == null || features.size() == 0) {
            features = DEFAULT_ANALYZE_FEATURES;
        }

        try (CloseableFile file = blob.getCloseableFile();
                FileInputStream fis = new FileInputStream(file.getFile());
                FileChannel channel = fis.getChannel()) {

            ByteBuffer fileByteBuffer = ByteBuffer.allocate((int) channel.size());
            channel.read(fileByteBuffer);
            fileByteBuffer.flip(); // prepare for reading

            AnalyzeDocumentRequest request = new AnalyzeDocumentRequest().withFeatureTypes(
                    features.toArray(new String[0])).withDocument(new Document().withBytes(fileByteBuffer));

            AnalyzeDocumentResult result = textractClient.analyzeDocument(request);

            return result;

        } catch (IOException e) {
            throw new NuxeoException(e);
        }

    }

    public String analyzeGetText(TextractUtils.Granularity granularity, List<String> features, Blob blob) {

        AnalyzeDocumentResult result = analyze(features, blob);

        String text = TextractUtils.getAllTextJoined(result::getBlocks, granularity, "\n");

        return text;
    }

    public String analyzeGetRawResultJsonString(List<String> features, Blob blob) {

        AnalyzeDocumentResult result = analyze(features, blob);

        ObjectMapper mapper = new ObjectMapper();
        var jsonNode = mapper.valueToTree(result);

        return jsonNode.toString();

    }

    // ========================================> DetectDocumentText
    /**
     * WARNING: assumes the blob is on S3
     */
    public DetectDocumentTextResult detectDocumentText(String blobKeyOnS3) {

        String s3Path = bucketPrefix + blobKeyOnS3;
        DetectDocumentTextRequest request = new DetectDocumentTextRequest().withDocument(
                new Document().withS3Object(new S3Object().withName(s3Path).withBucket(bucket)));

        DetectDocumentTextResult result = textractClient.detectDocumentText(request);

        return result;
    }

    public DetectDocumentTextResult detectDocumentText(Blob blob) {

        // If S3, use it directly
        String s3BlobKey = getS3BlobKey(blob);
        if (StringUtils.isNotBlank(s3BlobKey)) {
            return detectDocumentText(s3BlobKey);
        }

        try (CloseableFile file = blob.getCloseableFile();
                FileInputStream fis = new FileInputStream(file.getFile());
                FileChannel channel = fis.getChannel()) {

            ByteBuffer fileByteBuffer = ByteBuffer.allocate((int) channel.size());
            channel.read(fileByteBuffer);
            fileByteBuffer.flip(); // prepare for reading

            DetectDocumentTextRequest request = new DetectDocumentTextRequest().withDocument(
                    new Document().withBytes(fileByteBuffer));

            DetectDocumentTextResult result = textractClient.detectDocumentText(request);

            return result;

        } catch (IOException e) {
            throw new NuxeoException(e);
        }

    }

    public String detectDocumentTextGetText(TextractUtils.Granularity granularity, Blob blob) {

        DetectDocumentTextResult result = detectDocumentText(blob);

        String text = TextractUtils.getAllTextJoined(result::getBlocks, granularity, "\n");

        return text;
    }

    public String detectDocumentTextGetRawResultJsonString(Blob blob) {

        DetectDocumentTextResult result = detectDocumentText(blob);

        ObjectMapper mapper = new ObjectMapper();
        var jsonNode = mapper.valueToTree(result);

        return jsonNode.toString();

    }
}
