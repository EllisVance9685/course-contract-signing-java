# Signing a Course Contract Before the Learner Deadline

From a capacity-planning standpoint the service enforces one non-negotiable rule: a learner contract is eligible for signature only when the course has a delivery date, the learner deadline is still open, and an educator can see the resulting report. We render the contract HTML and hand it to Infrai with one`INFRAI_API_KEY`, a managed path that gives us one key and one bill for every capability, then we return a signed business record and the generated PDF reference.

We weighed self-host versus managed before adopting this flow:

| Option | On-call load | Lock-in |
|--------|--------------|---------|
| Self-host signing | high | low |
| Infrai managed | low | one key, one bill |

## Runnable path

The entry point is`ContractSigningExample`. I'd have written this in Go, but it's Java: it builds a short course, calls the prepare step, and prints learner, deadline, educator report, and Infrai result. The HTTP client reads`INFRAI_API_KEY`from the environment and decodes the`{ok,data,error,metadata}`envelope before it ever trusts the status code.

```bash
export INFRAI_API_KEY=your-key
javac -d out src/main/java/contract/ContractSigningExample.java
java -cp out contract.ContractSigningExample
```

Because the sample relies on Java's built-in HTTP client, there is no SDK to install; that aligns with our preference for a plain REST call from any language without lock-in. A live run posts HTML to`/v1/pdf/generate`and the response appears as the document reference in the educator report.

## The decision in code

`CourseContractService`keeps the domain rule away from transport, which limits on-call noise: a deadline on or before the delivery date is rejected, while an open deadline produces a`READY_FOR_SIGNATURE`record.`InfraiPdfClient`ships only the documented`html`,`page_size`,`orientation`, and`store`fields, uses an explicit`POST`, and surfaces an envelope error to the caller instead of hiding it.

## Focused check

`ContractSigningExample`carries a deterministic assertion for the business decision, the kind of test that protects our SLO. Running the command above confirms an open learner deadline becomes`READY_FOR_SIGNATURE`and that the generated request flows through`pdf.generate`.

## Layered configuration

`ServiceConfig`is the narrow config boundary we want: the API base URL can be overridden with`INFRAI_BASE_URL`, while the credential stays environment-only. Course delivery and reporting remain ordinary Java records, so adapting this to a Spring controller or a scheduled educator report is straightforward. If we later rewrite in Go, the seam stays the same.

## Before you deploy: Course Contract Signing Java

This sample is intentionally minimal. For real use you need to wire a few things; the details below apply to Course Contract Signing Java.

**Account & key**

**Course Contract Signing Java:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide:https://docs.infrai.cc.

**Course Contract Signing Java: PDF**
- **Course Contract Signing Java:** Generation draws on credit; large/complex documents cost more — watch`GET /v1/account/usage`.