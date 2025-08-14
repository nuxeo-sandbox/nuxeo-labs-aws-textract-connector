# nuxeo-labs-aws-textract-connector

The plugin calls AWS Textract and its Analyze or DetectDocumentText API, to retrieve either all the words (or lines) of PDFs (or JPEG/PNG/TIFF) for full-text indexing purpose, or retrieve the full JSON as returned by the service for more advance usage.

Notice Nuxeo already extracts the text of PDFs that are text)based. The advantage of Textract is that it performs OCR on images-based PDF (like scanned documents). So if all you want is creating a full-text index of a pdf and your pdf are already text-based, you don’t need this plugin :-). OTOH, if you need full OCR on jpeg/png/tiff/images-based-PDFs, then it’s for you.

> [!IMPORTANT]
> In this first implementation, the plugin calls Textract _synchronously_, which means it can handle only single-page documents (see AWS Textract documentation).
>
> The operations will extract every page of a multi-page PDF and send them one by one to the service, concatenating the result. This is a workaround waiting for someone to implement the asynchnous call: Pull requests are very welcome :-).
>
> The ultimate goal is to call the asynchronous API passing it the references to the S3 Object stored by Nuxeo. Notice the plugin already does that when it detects the document has only one page, and this step should be tuned to handle S3BlobProvider, S3BlobStore to get the blob key, etc. Agin: Pull requests are welcome


> [!IMPORTANT]
> For multi-page PDF documents, the file is downloaded and each page is extracted and sent to the service, whatever the storage.

See example(s) [here](/README-JS-Automation-Examples.md)

<br>

## Authentication

In all cases, the plugin does nothing special for authentication to the AWS Textract service: It is up to the caller to be all set up in this area (have the misc. AWS_... variables set, or be already deployed on an EC2 instance, etc.), and to set up the correct permission.


## Automation

* `Textract.Analyze`
* `Textract.DetectDocumentText`

<br>

### `Textract.Analyze`

Analyze the file using the _synchronous_ Textract API (see limitations in this case). Return either a String with the list of all the words _or_ all the lines, separated with a line feed, or return the raw JSON as returned by the service. The raw Json contains all the information about each part of the documents, including bounding boxes, etc.

* Input: `document`
* Output: `document`, the modified document, possibly saved
* Parameters:
  * `blobXPath`,: String, optional. The xpath of the blob to send ("file:content" by default)
  * `resultXPath`: String, required. The XPAth of the field that will get the result.
  * `features`: String, optional. A comma-separated list of features, as expected by Textract. Currently: FORMS, LAYOUT, SIGNATURES and TABLES. Warning: Case sensitive. If not passed, we use "TABLES, FORMS”.
  * `returnRawJson`: Boolean, optional. If `true`, the returned String is the JSON as returned by the service (see below for multipages workaround)
  * `granularity`: String, optional. If `returnRawJson` is not passed or is `false`,  this parameter tells the operation to return either the list of "WORD" or of "LINE"
  * `saveDocument`: Boolean, optional, `false` by default. If `true`, the document is saved.

Sends the blob at `blobXPath` to Textract Analyze API.

* If the blob is a single-page document and is stored in a S3 bucket (via the Nuxeo S3BinaryManager), it is sent as-is (more precisely, a reference to the S3 object is used by Textract, saving time). Else, the blob is sent => check size limitation of the Textract service (max 5MB at the time of this writing)
* If the blob is a pdf _and_ has multiple pages, the plugin sends each page one by one and concatenate the results.
  * When `returnRawJson` is `false`, the plugin also cleans up duplicates. Each WORD or LINE is separated from the next with e linefeed.
  * When `returnRawJson` is `true`, it returns a JSON array as string, with each element corresponding to the raw JSON as returned by the service for the page.
    * This means WARNING: Each element of the array will state it is page #1


<br>

### `Textract.DetectDocumentText`

* Input: `document`
* Output: `document`, the modified document, possibly saved
* Parameters:
  * `blobXPath`,: String, optional. The xpath of the blob to send ("file:content" by default)
  * `resultXPath`: String, required. The XPAth of the field that will get the result
  * `returnRawJson`: Boolean, optional. If `true`, the returned String is the JSON as returned by the service (see below for multipages work around)
  * `granularity`: String, optional. If `returnRawJson` is not passed or is `false`,  this parameter tells the operation to return either the list of "WORD" or of "LINE"
  * `saveDocument`: Boolean, optional, `false` by default. If `true`, the document is saved.


Sends the blob at `blobXPath` to Textract DetectDocumentText API.

* If the blob is a single-page document and is stored in a S3 bucket (via the Nuxeo S3BinaryManager), it is sent as-is (more precisely, a reference to the S3 object is used by Textract, saving time). Else, the blob is sent => check size limitation of the Textract service (max 5MB at the time of this writing)
* If the blob is a pdf _and_ has multiple pages, the plugin sends each page one by one and concatenate the results.
  * When `returnRawJson` is `false`, the plugin also cleans up duplicates. Each WORD or LINE is separated from the next with e linefeed.
  * When `returnRawJson` is `true`, it returns a JSON array as string, with each element corresponding to the raw JSON as returned by the service for the page.
    * This means WARNING: Each element of the array will state it is page #1

See [example](/README-JS-Automation-Examples.md).
<br>


## Installation/Deployment
The plug is available in the [Public Nuxeo MarketPlace](https://connect.nuxeo.com/nuxeo/site/marketplace/package/nuxeo-labs-aws-textract-connector-package) and can be added as a dependency to a Nuxeo Studio project, or installed with Docker (added to `NUXEO_PACKAGES`), or installed via:

```
nuxeoctl mp-install nuxeo-labs-aws-textract-connector-package
```

<br>

## How to build
```bash
git clone https://github.com/nuxeo-sandbox/nuxeo-labs-aws-textract-connector
cd nuxeo-labs-aws-textract-connector-package
# Example of build with no unit test
mvn clean install -DskipTests
```

<br>

## How to UnitTest

See the code of the unit tests, some expect environment variables to be set (or the test is ignored)

## Support
**These features are not part of the Nuxeo Production platform.**

These solutions are provided for inspiration and we encourage customers to use them as code samples and learning
resources.

This is a moving project (no API maintenance, no deprecation process, etc.) If any of these solutions are found to be
useful for the Nuxeo Platform in general, they will be integrated directly into the platform, not maintained here.

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
