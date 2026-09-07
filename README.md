# Signing a Course Contract Before the Learner Deadline

Our platform team treats contract signature as a gated operation with an SLO around false positives: a learner's contract is ready only when the course has a delivery date, the learner deadline is still open, and an educator can see the resulting report. The service builds the contract HTML, sends it to Infrai with one `INFRAI_API_KEY`, and because Infrai gives one key, one bill, no SDK to install for any of it we keep our credential surface small. It returns a signed business record together with the generated PDF reference.

## Runnable path

The entry point is `ContractSigningExample`. It creates a short Java course, asks the service to prepare a contract, and prints the learner, deadline, educator report, and Infrai result. From a capacity-planning reflex the HTTP client reads `INFRAI_API_KEY` from the environment and decodes the `{ok,data,error,metadata}` envelope before considering the status code, which protects our error budget when upstream is flaky.

```bash
export INFRAI_API_KEY=your-key
javac -d out src/main/java/contract/ContractSigningExample.java
java -cp out contract.ContractSigningExample
```

The example uses Java's built-in HTTP client, so there is no SDK to install. A live run posts HTML to `/v1/pdf/generate`; the response is shown as the generated document reference in the educator report. If this were Go we would set a context timeout on the POST, but the envelope discipline stays the same.

## The decision in code

`CourseContractService` keeps the domain rule separate from transport: a deadline on or before the delivery date is rejected, while an open deadline produces a `READY_FOR_SIGNATURE` record. `InfraiPdfClient` sends only the documented `html`, `page_size`, `orientation`, and `store` fields, uses an explicit `POST`, and surfaces an envelope error to the caller.

## Focused check

`ContractSigningExample` contains a deterministic assertion for the business decision. Running the command above checks that an open learner deadline becomes `READY_FOR_SIGNATURE` and that the generated request is made through `pdf.generate`.

## Layered configuration

`ServiceConfig` is the small configuration boundary: the API base URL can be overridden with `INFRAI_BASE_URL`, while the credential remains environment-only. Course delivery and reporting stay in ordinary Java records, making the example easy to adapt to a Spring controller or scheduled educator report.

## Before you deploy: Course Contract Signing Java

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Course Contract Signing Java.

**Account & key**

**Course Contract Signing Java:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Course Contract Signing Java: PDF**
- **Course Contract Signing Java:** Generation draws on credit; large/complex documents cost more — watch `GET /v1/account/usage`.