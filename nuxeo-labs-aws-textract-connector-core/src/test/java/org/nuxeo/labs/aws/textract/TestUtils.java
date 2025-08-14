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

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.blob.SimpleManagedBlob;

/**
 * @since TODO
 */
public class TestUtils {

    public static final String BUCKET = System.getenv("TEST_TEXTRACT_BUCKET");

    public static final String BUCKET_PREFIX = System.getenv("TEST_TEXTRACT_BUCKET_PREFIX");

    public static final String REGION = System.getenv("TEST_TEXTRACT_REGION");

    public static final String DIGEST = System.getenv("TEST_TEXTRACT_BLOB_DIGEST");

    public static final String WORDS_TO_FIND = System.getenv("TEST_TEXTRACT_WORDS_TO_FIND");
    
    public static boolean hasTestEnvVariables() {
        return StringUtils.isNoneBlank(BUCKET, BUCKET_PREFIX, REGION, DIGEST);
    }
    
    public static ManagedBlob createFakeS3Blob(String digest) {

        // Fake a S3 blob (TestUtils.DIGEST is supposed to be S3 blob)
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.key = "s3:" + digest;
        blobInfo.digest = digest;
        ;
        blobInfo.filename = "somefile.pdf";
        blobInfo.mimeType = "application/pdf";
        ManagedBlob blob = new SimpleManagedBlob(blobInfo);

        return blob;
    }

}
