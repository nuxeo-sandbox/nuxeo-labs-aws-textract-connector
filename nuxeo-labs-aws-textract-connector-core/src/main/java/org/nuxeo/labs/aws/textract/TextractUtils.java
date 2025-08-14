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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.core.util.BlobList;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.platform.pdf.PDFInfo;
import org.nuxeo.ecm.platform.pdf.PDFPageExtractor;

import com.amazonaws.services.textract.model.Block;

/**
 * @since TODO
 */
public class TextractUtils {
    
    public enum Granularity {
        WORD, LINE
    }

    private TextractUtils() {
        
    }

    /** Returns all text at the requested granularity (WORD or LINE). */
    public static List<String> getAllText(Supplier<List<Block>> blocksSupplier, Granularity granularity) {

        List<Block> blocks = Objects.requireNonNullElseGet(blocksSupplier.get(), List::of);

        String wanted = granularity.name(); // "WORD" or "LINE"
        return blocks.stream()
                     .filter(b -> wanted.equals(b.getBlockType()))
                     .map(Block::getText)
                     .filter(Objects::nonNull)
                     // Skip pure punctuation
                     .filter(text -> !text.matches("\\p{Punct}+"))
                     // Keep only unique, preserving first-seen order
                     .collect(Collectors.toCollection(java.util.LinkedHashSet::new))
                     .stream()
                     .toList();
    }

    /** Convenience: join the extracted text with a separator (e.g., " ", "\n"). */
    public static String getAllTextJoined(Supplier<List<Block>> blocksSupplier, Granularity granularity,
            String separator) {
        return String.join(separator, getAllText(blocksSupplier, granularity));
    }
    
    /**
     * Remove the duplicate lines.
     * 
     * @param input
     * @return
     * @since TODO
     */
    public static String removeDuplicates(String input, String separator) {
        
        if(StringUtils.isBlank(separator)) {
            separator = "\n";
        }
        
        Set<String> seen = new LinkedHashSet<>();
        String result = Arrays.stream(input.split("\\R"))
                       .filter(line -> seen.add(line.toLowerCase()))
                       .collect(Collectors.joining(separator));
        
        return result;
    }
    
    /**
     * Return null if the input blob has one page or is not pdf
     * @param blob
     * @return
     * @since TODO
     */
    public static BlobList splitPDFIfMoreThanOnePage(Blob blob) {
        
        String mimeType = blob.getMimeType();
        int pages = 0;
        
        BlobList blobList = new BlobList();
        if ("application/pdf".equals(mimeType)) {
            PDFInfo pdfInfo = new PDFInfo(blob);
            pdfInfo.run();
            pages = pdfInfo.getNumberOfPages();
            if (pages > 1) {
                PDFPageExtractor pe = new PDFPageExtractor(blob);
                for (int i = 1; i <= pages; i++) {
                    Blob onePage = pe.extract(i, i);
                    blobList.add(onePage);
                }
            }
        }
        
        return blobList;
        
    }
    
    /**
     * Delete the files linked to the blob, ignoring errors.
     * To be use when you are sure the blobs hold temp. files.
     * 
     * @param blobList
     * @since TODO
     */
    public static void deleteFilesSilently(BlobList blobList) {
        
        for (Blob oneBlob : blobList) {
            try {
                oneBlob.getFile().delete();
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
