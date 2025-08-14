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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(AutomationFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.labs.aws.textract.nuxeo-labs-aws-textract-connector-core")
public class TestOperations {
    
    public static final String TEST_PDF_IMAGE_PATH = "files/test-images-based-3pages.pdf";

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;
    
    @Test
    public void shouldAnalyzeLocalBlobAndGetWords() throws Exception {

        Assume.assumeTrue("No TEST_TEXTRACT_... env. variables set => ignoring the test",
                TestUtils.hasTestEnvVariables());
        
        File file = FileUtils.getResourceFileFromContext(TEST_PDF_IMAGE_PATH);
        Blob b = new FileBlob(file);
        b.setMimeType("application/pdf");
        b.setFilename("theblob.pdf");
        
        DocumentModel doc = session.createDocumentModel("/", "testfile", "File");
        doc.setPropertyValue("file:content", (Serializable) b);
        doc = session.createDocument(doc);
        
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(doc);
        Map<String, Object> params = new HashMap<>();
        params.put("resultXPath", "dc:description");
        params.put("features", "TABLES,FORMS,LAYOUT,SIGNATURES");
        // Other params => default values
        // Only region is needed but code expects the others
        params.put("bucket", TestUtils.BUCKET);
        params.put("bucketPrefix", TestUtils.BUCKET_PREFIX);
        params.put("region", TestUtils.REGION);

        DocumentModel modifiedDoc = (DocumentModel) automationService.run(ctx, AnalyzeOp.ID, params);
        assertNotNull(modifiedDoc);

        String desc = (String) modifiedDoc.getPropertyValue("dc:description");
        assertFalse(StringUtils.isBlank(desc));
        
        desc = desc.toLowerCase();
        assertTrue(desc.indexOf("nuxeo") > -1);
        assertTrue(desc.indexOf("cloud-native") > -1);
        
        /*
        File resultFile = new File("/Users/BLAHBLAH/Downloads/resultFile.txt");
        org.apache.commons.io.FileUtils.writeStringToFile(resultFile, desc, StandardCharsets.UTF_8);
        */
    }
    
    @Test
    public void shouldAnalyzeLocalBlobAndGetJson() throws Exception {

        // We need S3 infos to connect to the textract service
        Assume.assumeTrue("No TEST_TEXTRACT_... env. variables set => ignoring the test",
                TestUtils.hasTestEnvVariables());
        
        File file = FileUtils.getResourceFileFromContext(TEST_PDF_IMAGE_PATH);
        Blob b = new FileBlob(file);
        b.setMimeType("application/pdf");
        b.setFilename("theblob.pdf");
        
        DocumentModel doc = session.createDocumentModel("/", "testfile", "File");
        doc.setPropertyValue("file:content", (Serializable) b);
        doc = session.createDocument(doc);
        
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(doc);
        Map<String, Object> params = new HashMap<>();
        params.put("resultXPath", "dc:description");
        params.put("features", "TABLES,FORMS,LAYOUT,SIGNATURES");
        params.put("returnRawJson", true);
        // Other params => default values
        // Only region is needed but code expects the others
        params.put("bucket", TestUtils.BUCKET);
        params.put("bucketPrefix", TestUtils.BUCKET_PREFIX);
        params.put("region", TestUtils.REGION);

        DocumentModel modifiedDoc = (DocumentModel) automationService.run(ctx, AnalyzeOp.ID, params);
        assertNotNull(modifiedDoc);

        String desc = (String) modifiedDoc.getPropertyValue("dc:description");
        assertFalse(StringUtils.isBlank(desc));
        
        JSONArray descJson = new JSONArray(desc);
        assertNotNull(descJson);
        
        
        File resultFile = new File("/Users/thibaud.arguillere/Downloads/resultFile.json");
        org.apache.commons.io.FileUtils.writeStringToFile(resultFile, descJson.toString(2), StandardCharsets.UTF_8);
        
        
        desc = desc.toLowerCase();
        assertTrue(desc.indexOf("nuxeo") > -1);
        assertTrue(desc.indexOf("cloud-native") > -1);
    }

    @Test
    public void shouldAnalyzeS3BlobInDocAndGetWords() throws Exception {
        
        // We need S3 infos to connect to the textract service
        Assume.assumeTrue("No TEST_TEXTRACT_... env. variables set => ignoring the test",
                TestUtils.hasTestEnvVariables());

        Blob b = Blobs.createBlob("whatever");
        b.setDigest(TestUtils.DIGEST);

        DocumentModel doc = session.createDocumentModel("/", "testfile", "File");
        doc.setPropertyValue("file:content", (Serializable) b);
        doc = session.createDocument(doc);

        OperationContext ctx = new OperationContext(session);
        ctx.setInput(doc);
        Map<String, Object> params = new HashMap<>();
        params.put("resultXPath", "dc:description");
        // Other params => default values
        // Special for the test
        params.put("bucket", TestUtils.BUCKET);
        params.put("bucketPrefix", TestUtils.BUCKET_PREFIX);
        params.put("region", TestUtils.REGION);

        DocumentModel modifiedDoc = (DocumentModel) automationService.run(ctx, AnalyzeOp.ID, params);
        assertNotNull(modifiedDoc);

        String desc = (String) modifiedDoc.getPropertyValue("dc:description");
        assertFalse(StringUtils.isBlank(desc));

        desc = desc.toLowerCase();
        List<String> toFind = Arrays.stream(TestUtils.WORDS_TO_FIND.toLowerCase().split(","))
                                    .map(String::trim) // remove spaces
                                    .collect(Collectors.toList());
        for (String word : toFind) {
            assertTrue(desc.indexOf(word) > -1);
        }

    }

    // Ignore because  the code requires to deploy providers and all, and the local @Deploy don't work for whatever reason
    @Ignore
    @Test
    @Deploy("org.nuxeo.ecm.core.storage.binarymanager.s3")
    @Deploy("org.nuxeo.ecm.core.storage.binarymanager.s3.tests")
    @Deploy("org.nuxeo.labs.aws.textract.nuxeo-labs-aws-textract-connector-core:s3-blob-provider.xml")
    public void shouldAnalyzeS3BlobInDocAndGetJson() throws Exception {

        Assume.assumeTrue("No TEST_TEXTRACT_... env. variables set => ignoring the test",
                TestUtils.hasTestEnvVariables());

        Blob b = TestUtils.createFakeS3Blob(TestUtils.DIGEST);

        DocumentModel doc = session.createDocumentModel("/", "testfile", "File");
        doc.setPropertyValue("file:content", (Serializable) b);
        doc = session.createDocument(doc);

        OperationContext ctx = new OperationContext(session);
        ctx.setInput(doc);
        Map<String, Object> params = new HashMap<>();
        params.put("resultXPath", "dc:description");
        params.put("returnRawJson", true);
        // Other params => default values
        // Special for the test
        params.put("bucket", TestUtils.BUCKET);
        params.put("bucketPrefix", TestUtils.BUCKET_PREFIX);
        params.put("region", TestUtils.REGION);

        DocumentModel modifiedDoc = (DocumentModel) automationService.run(ctx, AnalyzeOp.ID, params);
        assertNotNull(modifiedDoc);

        String desc = (String) modifiedDoc.getPropertyValue("dc:description");
        assertFalse(StringUtils.isBlank(desc));

        JSONObject descJson = new JSONObject(desc);
        assertNotNull(descJson);

        assertTrue(descJson.has("blocks"));
        assertTrue(descJson.getJSONArray("blocks").length() > 0);

        // Of course, we can find the wors in this
        desc = desc.toLowerCase();
        List<String> toFind = Arrays.stream(TestUtils.WORDS_TO_FIND.toLowerCase().split(","))
                                    .map(String::trim) // remove spaces
                                    .collect(Collectors.toList());
        for (String word : toFind) {
            assertTrue(desc.indexOf(word) > -1);
        }

    }
}
