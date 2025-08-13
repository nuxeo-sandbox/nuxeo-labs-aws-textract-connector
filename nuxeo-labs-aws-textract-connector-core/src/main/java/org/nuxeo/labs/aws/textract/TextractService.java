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
/*
 * TODO Handle the Blob in a safest mode since here we assume it is stored on S3, with the digest as key. The proper way
 * to handle that is:
 * - Check that it's a ManagedBlob first, and also that its BlobProvider's BlobStore, once unwrapped is an S3BlobStore
 * - Use ManagedBlob.getKey, not getDigest
 * - Call stripBlobKeyPrefix and stripBlobKeyVersionSuffix on the blob key too
 * - Call config.bucketKey(key) to add back the bucket prefix and deal with subdirs
 */
public class TextractService {

    protected String bucket;

    protected String bucketPrefix;

    protected String region;

    protected AmazonTextract textractClient;

    protected static TextractService instance = null;

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
    public AnalyzeDocumentResult analyze(List<String> features, String blobDigest) {

        if (features == null || features.size() == 0) {
            features = DEFAULT_ANALYZE_FEATURES;
        }

        /*
         * Test with sending binary (it works like a charm)
         * File file = new File("/Users/thibaud.arguillere/Downloads/aaTest-19.pdf");
         * try (FileInputStream fis = new FileInputStream(file);
         * FileChannel channel = fis.getChannel()) {
         * ByteBuffer fileByteBuffer = ByteBuffer.allocate((int) channel.size());
         * channel.read(fileByteBuffer);
         * fileByteBuffer.flip(); // prepare for reading
         * AnalyzeDocumentRequest request = new AnalyzeDocumentRequest().withFeatureTypes(features.toArray(new
         * String[0]))
         * .withDocument(new Document().withBytes(fileByteBuffer));
         * AnalyzeDocumentResult result = textractClient.analyzeDocument(request);
         * return result;
         * } catch (IOException e) {
         * throw new NuxeoException(e);
         * }
         */

        String s3Path = bucketPrefix + blobDigest;
        AnalyzeDocumentRequest request = new AnalyzeDocumentRequest().withFeatureTypes(features.toArray(new String[0]))
                                                                     .withDocument(new Document().withS3Object(
                                                                             new S3Object().withName(s3Path)
                                                                                           .withBucket(bucket)));

        AnalyzeDocumentResult result = textractClient.analyzeDocument(request);

        return result;

    }

    public AnalyzeDocumentResult analyze(List<String> features, Blob blob) {

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

    public String analyzeGetText(TextractUtils.Granularity granularity, List<String> features, String blobDigest) {

        AnalyzeDocumentResult result = analyze(features, blobDigest);

        String text = TextractUtils.getAllTextJoined(result::getBlocks, granularity, "\n");

        return text;
    }

    public String analyzeGetRawResultJsonString(List<String> features, String blobDigest) {

        AnalyzeDocumentResult result = analyze(features, blobDigest);

        ObjectMapper mapper = new ObjectMapper();
        var jsonNode = mapper.valueToTree(result);

        return jsonNode.toString();

    }

    // ========================================> DetectDocumentText
    protected DetectDocumentTextResult detectDocumentText(String blobDigest) {

        String s3Path = bucketPrefix + blobDigest;
        DetectDocumentTextRequest request = new DetectDocumentTextRequest().withDocument(
                new Document().withS3Object(new S3Object().withName(s3Path).withBucket(bucket)));

        DetectDocumentTextResult result = textractClient.detectDocumentText(request);

        return result;
    }

    public String detectDocumentTextGetText(TextractUtils.Granularity granularity, String blobDigest) {

        DetectDocumentTextResult result = detectDocumentText(blobDigest);

        String text = TextractUtils.getAllTextJoined(result::getBlocks, granularity, "\n");

        return text;
    }

    public String detectDocumentTextGetRawResultJsonString(String blobDigest) {

        DetectDocumentTextResult result = detectDocumentText(blobDigest);

        ObjectMapper mapper = new ObjectMapper();
        var jsonNode = mapper.valueToTree(result);

        return jsonNode.toString();

    }
}
