# nuxeo-labs-aws-textract-connector: Nuxeo JavaScript Automation Examples

## Get Raw Json and Massage It

In this example, we send a PDF that has more than one page, retrieve the raw JSON fro Amazon textract and massage it to:

* Extract all words
* Extract all lines
* Simplify the JSON, so we get the words/lines with their bounding boxes in the document, wihtout all the other Amazon Textract infos.

> [!IMPORTANT]
> In this example, we assume the custom `textract` schema is available for the input document (it is not provided by the plugin you have to add it in Nuxeo Studio)

> [!NOTE]
> In a prod. environment, you likely don't need all these, this example is to be used, well, as an example :-)

> [!TIP]
> The operation can take time and should always be ran asynchronously. For example, in an asynchronous `documentCreated` EventHandler.

```javascript
/* Extracts raw json, all words, all lines and reformat the JSON
   input: document
   output: document
*/
function run(input, params) {
  
  var words, lines, blob, mimeType, json, finalJson,
      finalObj, finalJsonLines, finalJsonWords, page;
    
  // ==============================> Sanitycheck
  blob = input["file:content"];
  if(!blob) {
    Console.warn("  Input doc has no blob => doing nothing.");
    return doc;
  }
  switch(blob.getMimeType()) {
    case("application/pdf"):
    case("image/png"):
    case("image/jpg"):
    case("image/tiff"):
      break;
      
    default:
      Console.warn("  Input blob is not pdf, png, jpeg or tiff => doing nothing.");
      return doc;
  }
  
  // ==============================> Process
  input = Textract.DetectDocumentText(
    input, {
      "blobXPath": "file:content",
      "resultXPath": "textract:jsonRaw",
      "returnRawJson": true,
      "saveDocument": false // saved later
    });
  
  json = JSON.parse(input["textract:jsonRaw"]);
  // See plugin documentation. Pages were sent one by one, and the plugin returns an array
  // of each page. But each object, as returned by textract, references page 1, of course.
  // So, here, we massage all this to get a smaller json.
  finalJsonLines = [];
  finalJsonWords = [];
  words = "";
  lines = "";
  page = 0;
  // the result json is an array, each element of the array is the raw result of AWS Textract call
  json.forEach(function(oneOcr) {
    page += 1;
    oneOcr.blocks.forEach(function(oneBlock) {
      if(oneBlock.blockType) {
        finalObj = null;
        switch(oneBlock.blockType) {
          case "WORD":
          case "LINE":
            finalObj = {
              "page": page,
              "boundingBox": oneBlock.geometry.boundingBox,
              "text": oneBlock.text,
              "confidence": oneBlock.confidence
            };
            switch(oneBlock.blockType) {
              case "WORD":
                finalJsonWords.push(finalObj);
                words += finalObj.text + "\n";
                break;
              case "LINE":
                finalJsonLines.push(finalObj);
                lines += finalObj.text + "\n";
                break;
            }
            break;
        }
      }
      
    });
  });
  
  finalJson = {
    "words": finalJsonWords,
    "lines": finalJsonLines
  };
  
  // Saving as pretty json. Takes way too much space on disk but this is A POC
  // and anyway prod. will not save all these, just the relevant ones.
  input["textract:jsonRaw"] = JSON.stringify(json, null, 2);
  input["textract:jsonLight"] = JSON.stringify(finalJson, null, 2);
  input["textract:lines"] = words;
  input["textract:words"] = lines;
  input = Document.Save(input, {});
    
  return input;

}
```




<br>

## License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

<br>

## About Nuxeo
[Nuxeo Platform](https://www.hyland.com/solutions/products/nuxeo-platform) is an open source Content Services platform, written in Java. Data can be stored in both NoSQL & SQL
databases.

The development of the Nuxeo Platform is mostly done by Nuxeo employees with an open development model.

The source code, documentation, roadmap, issue tracker, testing, benchmarks are all public.

Typically, Nuxeo users build different types of information management solutions
for [document management](https://www.hyland.com/platform/content-management), 
and [digital asset management](https://www.hyland.com/platform/digital-asset-management) ....

It uses
schema-flexible metadata & content models that allow content to be repurposed to fulfill future use cases.

More information is available at [www.nuxeo.com](https://www.hyland.com/solutions/products/nuxeo-platform).
