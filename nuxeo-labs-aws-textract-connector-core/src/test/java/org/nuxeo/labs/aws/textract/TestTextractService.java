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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
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
public class TestTextractService {

    @Inject
    protected CoreSession session;

    @Test
    public void shouldAnalyzeAndGetAllForAS3Blob() throws Exception {

        Assume.assumeTrue("No TEST_TEXTRACT_... env. variables set => ignoring the test",
                TestUtils.hasTestEnvVariables());

        TextractService service = TextractService.getInstance(TestUtils.BUCKET, TestUtils.BUCKET_PREFIX,
                TestUtils.REGION);
        assertNotNull(service);

        Blob blob = TestUtils.createFakeS3Blob(TestUtils.DIGEST);
        service.setForceS3Key(true);
        List<String> features = List.of("TABLES", "FORMS", "LAYOUT", /* "QUERIES", */"SIGNATURES");
        String jsonStr = service.analyzeGetRawResultJsonString(features, blob);
        assertNotNull(jsonStr);

        // Find our words
        String words = jsonStr.toLowerCase();
        List<String> toFind = Arrays.stream(TestUtils.WORDS_TO_FIND.toLowerCase().split(","))
                                    .map(String::trim) // remove spaces
                                    .collect(Collectors.toList());
        for (String word : toFind) {
            assertTrue(words.indexOf(word) > -1);
        }

        // Get more
        File file = new File("/Users/thibaud.arguillere/Downloads/file-20.json");
        FileUtils.writeStringToFile(file, jsonStr, StandardCharsets.UTF_8);

    }

    @Test
    public void shouldAnalyzeAndGetWordsForAS3Blob() throws Exception {

        Assume.assumeTrue("No TEST_TEXTRACT_... env. variables set => ignoring the test",
                TestUtils.hasTestEnvVariables());

        TextractService service = TextractService.getInstance(TestUtils.BUCKET, TestUtils.BUCKET_PREFIX,
                TestUtils.REGION);
        assertNotNull(service);

        Blob blob = TestUtils.createFakeS3Blob(TestUtils.DIGEST);
        String words = service.analyzeGetText(TextractUtils.Granularity.WORD, null, blob);
        assertNotNull(words);

        words = words.toLowerCase();
        List<String> toFind = Arrays.stream(TestUtils.WORDS_TO_FIND.toLowerCase().split(","))
                                    .map(String::trim) // remove spaces
                                    .collect(Collectors.toList());
        for (String word : toFind) {
            assertTrue(words.indexOf(word) > -1);
        }

    }
}
