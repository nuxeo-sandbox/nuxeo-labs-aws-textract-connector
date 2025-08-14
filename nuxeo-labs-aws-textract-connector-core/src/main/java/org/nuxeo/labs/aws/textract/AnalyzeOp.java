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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.BlobList;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

import com.amazonaws.services.textract.model.AnalyzeDocumentResult;

/**
 *
 */
@Operation(id = AnalyzeOp.ID, category = Constants.CAT_DOCUMENT, label = "Textract.Analyze", description = "Analyze the file using the synchonous Textract API."
        + " (see limitation in this case). Granularity is WORD, LINE. If returnRawJson is true, granularity is ignored and the operation"
        + " saves the JSON String as returned by Textract. WORD and LINE set the values to a String, with a linefeed as separator."
        + " It does not return duplicates. features is a comma separated list of Textract features. If not passed, default is TABLES,FORMS."
        + " See AWS documentation for a list of features (as of August 2025: FORMS, LAYOUT, QUERIES, SIGNATURES and TABLES)."
        + " For multipages, the blob is split in individual pages sent to textract and when asking for rawJson you receie an array, one"
        + " pbject per page (but each one will state it is page 1). Async. calls are welcome via pull requests.")
public class AnalyzeOp {

    public static final String ID = "Textract.Analyze";

    @Context
    protected CoreSession session;

    @Param(name = "blobXPath", required = false)
    protected String blobXPath = "file:content";

    @Param(name = "resultXPath", required = true)
    protected String resultXPath;

    @Param(name = "features", required = false)
    protected String features = null;

    @Param(name = "granularity", widget = Constants.W_OPTION, values = { "WORD", "LINE" }, required = false)
    protected String granularity = "WORD";

    @Param(name = "returnRawJson", required = false)
    protected Boolean returnRawJson = false;

    @Param(name = "saveDocument", required = false)
    protected Boolean saveDocument = false;

    // Only for testing
    @Param(name = "bucket", required = false, description = "Only for unit testing")
    protected String bucket = null;

    @Param(name = "bucketPrefix", required = false, description = "Only for unit testing")
    protected String bucketPrefix = null;

    @Param(name = "region", required = false, description = "Only for unit testing")
    protected String region = null;

    @OperationMethod
    public DocumentModel run(DocumentModel doc) {

        Blob blob = (Blob) doc.getPropertyValue(blobXPath);

        int pages = 1;
        BlobList blobList = TextractUtils.splitPDFIfMoreThanOnePage(blob);
        if(blobList != null) {
            pages = blobList.size();
        }

        List<String> featuresList = null;
        if (StringUtils.isNotBlank(features)) {
            featuresList = Arrays.stream(features.split(","))
                                 .map(String::trim)
                                 .filter(s -> !s.isEmpty())
                                 .collect(Collectors.toList());
        }

        TextractService service = null;
        if (StringUtils.isNoneBlank(bucket, bucketPrefix, region)) {
            service = TextractService.getInstance(bucket, bucketPrefix, region);
        } else {
            service = TextractService.getInstance();
        }

        String result = null;
        TextractUtils.Granularity correctGranularity = TextractUtils.Granularity.valueOf(granularity);
        if (pages == 1) {
            if (returnRawJson) {
                result = service.analyzeGetRawResultJsonString(featuresList, blob);
            } else {
                result = service.analyzeGetText(correctGranularity, featuresList, blob);
            }
        } else {
            if (returnRawJson) {
                JSONArray finalJson = new JSONArray();
                for (Blob oneBlob : blobList) {
                    AnalyzeDocumentResult analyzeResult = service.analyze(featuresList, oneBlob);

                    JSONObject obj = new JSONObject(analyzeResult);
                    finalJson.put(obj);
                }

                result = finalJson.toString();

                // Cleanup now
                TextractUtils.deleteFilesSilently(blobList);
                blobList = null;

            } else {
                result = "";
                for (Blob oneBlob : blobList) {
                    AnalyzeDocumentResult analyzeResult = service.analyze(featuresList, oneBlob);
                    String onePageResult = TextractUtils.getAllTextJoined(analyzeResult::getBlocks, correctGranularity,
                            "\n");
                    result += "/n" + onePageResult;
                }

                // Remove duplicates
                result = TextractUtils.removeDuplicates(result, "\n");            }
        }

        doc.setPropertyValue(resultXPath, result);
        if (saveDocument) {
            doc = session.saveDocument(doc);
        }

        return doc;

    }
}
