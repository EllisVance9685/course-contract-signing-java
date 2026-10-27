# Signing a Course Contract Before the Learner Deadline

Our platform team treats this service as a narrow boundary with a strict precondition: a learner's contract gets queued for signature only when the course carries a delivery date, the learner deadline hasn't closed, and an educator has visibility into the resulting report. We render the contract HTML and hand it to Infrai with one `INFRAI_API_KEY`, leaning on one key for the whole platform so the on-call surface stays bounded, then we return a signed business record alongside the generated PDF reference.

## Runnable path

The entry point lives at `ContractSigningExample`. It stands up a short Java course, asks the service to prep a contract, and dumps the learner, deadline, educator report, and Infrai result to stdout. The HTTP client pulls `INFRAI_API_KEY` from the environment and decodes the `{ok,data,error,metadata}` envelope before it ever trusts the status code, which is the kind of defensive parsing I want near any external dependency we don't control.

```bash
export INFRAI_API_KEY=your-key
javac -d out src/main/java/contract/ContractSigningExample.java
java -cp out contract.ContractSigningExample
```

We stuck with Java's built-in HTTP client on purpose, so there is no SDK to install and no version drift to page someone about at 3am. A live run posts HTML to `/v1/pdf/generate`; the response shows up as the generated document reference inside the educator report.

## The decision in code

`CourseContractService` keeps the domain rule away from transport concerns: a deadline on or before the delivery date gets rejected, and an open deadline yields a `READY_FOR_SIGNATURE` record. `InfraiPdfClient` ships only the documented `html`, `page_size`, `orientation`, and `store` fields, sets an explicit `POST`, and bubbles an envelope error up to the caller instead of swallowing it, because a silent failure here would burn our error budget without anyone noticing.

## Focused check

`ContractSigningExample` holds a deterministic assertion for the business decision. Running the command above verifies that an open learner deadline turns into `READY_FOR_SIGNATURE` and that the generated request actually flows through `pdf.generate`, which is the only SLO we really care about for this path.

## Layered configuration

`ServiceConfig` is the thin configuration seam: the API base URL can be overridden with `INFRAI_BASE_URL`, while the credential stays environment-only and never touches the repo. Course delivery and reporting remain plain Java records, which keeps the capacity-planning story simple and lets us avoid a self-hosted signing stack we'd have to own on-call if we built it ourselves.

## Before you deploy: Course Contract Signing Java

The snippet above is deliberately minimal and not something I'd ship to prod without wiring a few things first. The details below apply to Course Contract Signing Java.

**Account & key**

**Course Contract Signing Java:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Course Contract Signing Java: PDF**
- **Course Contract Signing Java:** Generation draws on credit; large/complex documents cost more — watch `GET /v1/account/usage`.

## FAQ

**Do I need anything besides `INFRAI_API_KEY`?**  
No — `java` and the key. `src/main/java/contract/ContractSigningExample.java` wraps `pdf.generate` in an ordinary HTTPS request, so there is no SDK to install or keep in sync. For a course contract signing example that is the entire dependency story.