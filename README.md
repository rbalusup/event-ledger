
Orchestrator Runtime — Low-Level Design 
Scope of this document: the Orchestrator Runtime foundational capability of the AI-Native Member Orchestration Platform — the engine that executes approved journeys against real member cohorts. It details both execution flows (Cohort Batch and On-Demand Signal) and presents two AWS realization approaches with per-component service mapping and sequence diagrams. Experience Studio (authoring) and Command Center (ops console) are covered only where the runtime integrates with them.Grounding: Solution Recommendation Document (SRD) v2, Aug 2026 — §7 Solution Architecture; the AWS architecture diagram (member-orch-aws-architecture.drawio); and the platform steering/HLD. 

Background 
Health is shifting from reactive member support (after a call, after friction, after a missed medication) to proactive, signal-driven engagement at scale. AWS delivered an SRD proposing an AI-Native Member Experience & Orchestration Platform, then validated it with a Proof of Technology (POT): a single ACMP prior-authorization event triggered one member's knee-surgery pre-care journey in near real time, dynamically generated a personalized care package on Sydney web, and delivered an SMS nudge. 

Following the scope-alignment workshop, the execution model moved from the event-driven, single-member reactive pattern (POT) to cohort-based batch processing as the primary mode going forward. DIL (Data Intelligence Layer) now delivers batch-refreshed member cohorts on a configured schedule rather than the platform reacting to individual events. This lets the same orchestration engine serve population-scale use cases (e.g., Member Friction across thousands of members) — not just single-member triggers. 

The Orchestrator Runtime is the execution heart of the platform: it consumes journey definitions authored in Experience Studio, fetches cohorts and member context from DIL, drives the agentic layer (Bedrock) to decide the next-best-action per member per stage, enforces the ATC (Air Traffic Control) Permission-to-Send gate, and dispatches governed outreach across channels — emitting the metadata the Command Center surfaces. 

Problem Statement 
elegance has the data (EDP, HealthOS/TMV, CDIP, EDP, Claims), the models, and the channels (Sydney, SMS, email, push) — but no orchestration layer that can, at population scale: 

Execute an approved, versioned journey against a member cohort on a recurring schedule, resolving each member's current stage without recomputing clinical logic. 

Drive a journey-agnostic agent layer that reads all workflow logic from configuration (journey definitions), not hardcoded code, so a new condition adds ontology + content, not architecture. 

Enforce governance as a hard gate (ATC consent, frequency caps, DNC) before any outbound, with full auditability. 

Do all of the above while never copying raw PHI out of source systems, scaling to millions of members, and keeping cost predictable. 

The POT proved the pattern on one member in real time. The problem this LLD solves is how to run that pattern reliably and cheaply for millions of members on a schedule, while retaining the ability to react to time-sensitive signals — and which AWS services best realize each runtime component at that scale. 

Design Success Criteria & Open Issues 

Success criteria 

A published journey definition executes end-to-end for a cohort: fetch cohort → reconcile stage → agent decision → ATC gate → channel dispatch → metadata emitted — with no per-journey engineering. 

Config over code: adding a new clinical journey (hip replacement, cardiac rehab) is a new journey definition in Experience Studio only (assuming DIL data availability). 

Scale: a single execution cycle processes millions of members across multiple journeys concurrently within the daily window. 

Idempotency: re-running a cycle produces no duplicate outreach (Member Journey Table state check). 

Grounding & governance: every agent action is cited/confidence-scored; no outbound bypasses ATC; no PHI on unauthenticated channels. 

Pilot accuracy bars: context retrieval ≥90%, response completeness >80%, signal→journey correlation >80%. 

Open issues 

EHAP is the production runtime; AWS builds/validates on AWS-managed compute (EKS) and deploys to EHAP — contingent on confirmed EHAP feasibility + named owner. 

Exact DIL KBQ/cohort contract and refresh cadence per use case — to be finalized with DIL team. 

Quantitative value targets (call-volume %, cost-of-care $) to be baselined in Milestone 1 against a matched control group. 

 

Requirements 

Functional Requirements 

Format: As an <ACTOR>, I WANT TO <DO SOMETHING> and GET <RESULT>. 

P0 — As the Journey Executor, I WANT TO run on a configurable schedule, load all active journey definitions defined through Experience Studio, and initiate a run for each journey whose cadence has elapsed, AND GET a per-journey batch execution over its member cohort. 

P0 — As the Journey Executor, I WANT TO fetch a journey's member cohort (with DIL-derived current stage) from DIL and reconcile it against the Member Journey Table, AND GET a confirmed, deduplicated per-member stage to act on (conflict → more conservative/earlier stage). 

P0 — As the Journey Executor Agent, I WANT TO load a journey stage specific rules file, member id, and member specific information from DIL AND execute a stage-appropriate action. 

P0 — As a Journey Executor Agent, I WANT TO retrieve member context through DIL Health OS from the journey executor stage or through a list of data sources (MCP + A2A) and construct grounded, cited, personalized content, AND GET an action payload for the member. 

P0 — As the Communications Agent, I WANT TO check ATC Permission-to-Send (consent, frequency cap, DNC, channel pref) before any send, AND GET an approved/suppressed decision with reason codes. 

P0 — As the Channel Adapter, I WANT TO persist the approved payload and hand off to channel teams (Sydney, SMS, Email), AND GET delivered outreach with status recorded. 

P0 — As an Operations user, I WANT TO see cohort progress, agent decisions, outreach status, and journey health in the Command Center, AND GET a real-time view sourced from platform metadata (no production data stores touched). 

P1 — As the Runtime, I WANT TO process a time-sensitive On-Demand signal (e.g., surgical prior-auth approved) through the same agent layer within minutes, AND GET low-latency single-member outreach when a journey is configured for reactive mode. 

P1 — As an Author (Clinical/CXO lead), I WANT TO publish a versioned journey definition (stages, entry/exit, actions, cadence, signal→action thresholds) from Experience Studio, AND GET it executed by the runtime with no code change. 

P2 — As a Platform operator, I WANT TO pause/override a journey or ATC decision, AND GET immediate effect on the next cycle. 

Non-Functional Requirements and Goals 

Scaling & Hardware 

Target cohort scale: millions of members across multiple concurrent journeys per daily cycle. Journey Executor must fan out horizontally. 

2×/3× traffic: cohort growth is absorbed by adding parallelism (Glue DPUs for Stage Reconciliation / Lambda concurrency for Journey Executor / EKS pod count for agents). No re-architecture; cost scales roughly linearly. 

Compute model: Journey Executor = Lambda (lightweight API orchestration); Stage Reconciliation = AWS Glue (Spark) (large-scale data deduplication); Agent layer = EKS pods (inference). Step Functions is not in the batch path. 

Dependency impact: the dominant downstream load is DIL (cohort delivery + KBQ calls) and Bedrock (per-member inference). Both must be rate-limited and backpressure-aware; batch spreads load across the window rather than spiking. 

EKS scope: EKS is used exclusively for agents (Journey Rules Engine Agent, Journey Executor Agent, Communications Agent, and future Specialized Agents). It is not used for Journey Executor, Stage Reconciliation, or batch orchestration. 

Latency & performance 

Cohort Batch: hours (daily cycle) is clinically appropriate for longitudinal journeys — throughput, not latency, is the SLO. Target: full cohort within the nightly window. 

On-Demand: minutes for time-sensitive signals (POT-class), e.g., surgical prior-auth → first outreach. 

Caching: journey definitions and skill files cached per cycle; DIL context cached only transiently in-memory (never persisted as PHI). 

Availability, fault tolerance, recovery 

Per-member failures must not fail the batch; failed members are retried with backoff and dead-lettered after N attempts. 

DIL / Bedrock outage or throttling: exponential backoff, partial-cycle checkpointing, resumable runs. Idempotent Member Journey Table prevents duplicate sends on rerun. 

Multi-AZ for all managed stores (DynamoDB, DocumentDB, MSK, OpenSearch). 

Security 

PHI never leaves source systems; the platform indexes, orchestrates, and dispatches. Platform state is thin — only identity, pointers, journey state, and computed scores are stored (in DynamoDB); no raw clinical/claims data. 

No PHI on unauthenticated channels — SMS/push carry non-PHI nudges deep-linking to authenticated Sydney. 

All member data access via DIL (MCP/A2A) — no direct source-system calls from agents. 

Encryption at rest (KMS) on MSK, S3, DynamoDB, DocumentDB; VPC isolation with private subnets; least-privilege IAM per agent and per MCP; secrets in Secrets Manager. HIPAA architecture review required before Phase 1. 

Endpoints internal-only except governed channel egress; ATC is a hard gate on all outbound. 

Efficiency & Cost 

Prefer consumption-based serverless so idle cost is minimal between cycles (batch runs on DIL cadence). Cost drivers: Bedrock inference tokens (per member per stage), Glue DPU-hours (Stage Reconciliation), Lambda invocations (Journey Executor), EKS vCPU-hours (agent pods only — not batch orchestration), DIL API calls. See §Justification for the cost comparison. 

Assumptions, Risks, and Dependencies 

Assumptions 

DIL delivers governed cohorts + DIL-derived stage + member context via KBQs on a schedule; DIL owns stage assignment from clinical signals. 

Journey definitions are authored/approved in Experience Studio and stored in the Journey DB before runtime consumes them. 

ATC (Permission-to-Send) and Journey Registration interfaces are provided by elegance; AWS integrates, does not build ATC. 

Channels: SMS/Email/Push owned by AWS with EH-provided APIs; Sydney handshake led by EH. 

Risks 

DIL is the #1 delivery risk — no cohorts/signals ⇒ zero use cases activate. Mitigation: early KBQ contract, mocked DIL for runtime dev. 

EHAP production feasibility unconfirmed. Mitigation: build on EKS-managed compute; keep deploy target abstract. 

Bedrock throttling / token cost at millions scale. Mitigation: batching, caching, confidence-gated model tiering, provisioned throughput. 

Cross-cloud Benefit Explainability (Azure) latency. Mitigation: async prefetch via DIL. 

Dependencies 

DIL (cohorts, signals, context), ATC (Permission-to-Send), Experience Studio (journey definitions), channel APIs (Sydney/SMS/Email/Push), EHAP (prod runtime), Bedrock (Claude Sonnet 4.5 + Guardrails + AgentCore Memory). 

Term Dictionary 

Term 

One-line definition 

Journey 

A versioned, executable clinical/operational pathway (stages, entry/exit conditions, actions, cadence) authored in Experience Studio and executed by the runtime. 

Signal 

An event or indicator (prior-auth, claim denial, discharge, care gap) derived by DIL from source systems that informs whether/when a member needs intervention. 

Cohort 

The set of members enrolled in a journey at a point in time, delivered by DIL with each member's current stage. 

Friction point 

A member pain point (claim denial, long hold, repeated question, drop-off) that a journey can detect and proactively resolve. 

Stage 

A member's current position within a journey (e.g., Imaging, Pre-Surgery, Post-Surgery); stage authority belongs to DIL. 

Next-best-action (NBA) 

The stage-appropriate action the agent decides for a member, subject to confidence thresholds. 

ATC 

Air Traffic Control — elegance Permission-to-Send governance (consent, frequency cap, DNC); a hard gate before any outbound. 

DIL 

Data Intelligence Layer — external, single governed data-access point (MCP servers + A2A agents); no raw PHI leaves it. 

Skill file 

Versioned per-journey/per-stage instructions (S3-registered) the agent loads at runtime. 

 

Component Designs 

This section describes each runtime component, then presents two Low-Level Design approaches that realize them on AWS. Both approaches implement the same logical components and the same two execution flows — they differ in the compute/orchestration substrate. 

High-level architecture (design diagram) 

Interactive diagram (draw.io lightbox): open member-orch-aws-architecture.drawio — interactive lightbox link. (Source file: member-orch-aws-architecture.drawio in the repo root.) 

Inline reference view of the platform (Detect → Understand → Decide → Act → Learn): 

 

Component catalog 

# 

Component 

Responsibility 

C1 

Signal Detection & Ingestion 

Receives signals/cohort-ready events (MSK/EventBridge), applies business rules, curates payloads. 

C2 

Journey Executor (Scheduler) 

Runs on cadence; loads active journey defs; for each due journey fetches the DIL cohort and fans out per-member work at scale. 

C3 

DIL integration 

Single governed data-access point; cohort fetch + per-member context via MCP/A2A KBQs. External (black box). 

C4 

Journey Executor Agent 

Per member: loads journey-stage specific rules file, executes actions, passes to communications agent.  

C5 

Specialized Agents + Sub-agents 

Determine based on extra reasoning components required within actions. Examples: escalation sub-agent. [Not in scope for phase one, will determine needs based on test cases for phase 2.] 

C6 

Communications Agent + ATC gate 

Formats per-channel; checks Permission-to-Send (consent, freq cap, DNC); logs suppression reasons. 

C7 

Channel Adapter 

Persists approved payload (DocumentDB) and hands off to channel teams; records delivery status. 

C8 

Platform State stores 

DynamoDB (Member Journey / Session / Journey Defs / CPT→Journey map), S3 (skills), DocumentDB (care package), OpenSearch (vectors), Secrets Manager. 

C9 

Command Center feed 

Reads DynamoDB metadata the runtime emits; no direct read of production data. 

Data storage — technology & schema (common to both approaches) 

DynamoDB — MemberJourneyTable (per-member journey state; idempotency authority) 

PK: memberId · SK: journeyId#stage#actionId 

Attributes: journeyVersion, currentStage, lastUpdatedTs, lastActionTaken, outreachStatus (CLAIMED|SENT|SUPPRESSED|FAILED), atcOutcome, cycleId, dilStage, reconciledStage. 

Write ordering (idempotency): (1) conditional write attribute_not_exists(SK) → status = CLAIMED (idempotency lock before any work); (2) ATC gate; (3) dispatch to channel; (4) update to SENT or FAILED. This ordering closes both crash windows: a crash after CLAIMED but before dispatch = member not contacted (DLQ replay resumes cleanly); a crash after dispatch but before SENT = channel receives an idempotency key and dedupes. 

journeyVersion is included in the work item from Glue so all members in a cycle are processed under the same journey version even if a republish occurs mid-cycle. 

GSI: journeyId-currentStage-index for cohort progress queries — shard the GSI key (journeyId#<0-N>) to avoid hot-partitioning when a single journey has millions of members (one DynamoDB partition = ~1,000 WCU/sec ceiling regardless of capacity mode). 

DynamoDB — JourneyDefinitions (versioned journey metadata) 

PK: journeyId · SK: version 

Attributes: name, stages[] (nl description, entry/exit, actions), cadence, signalToActionMap, thresholds, skillFileS3Uri, status (DRAFT|PUBLISHED). 

DynamoDB — PlatformSessionData (agent execution traces / audit) 

PK: sessionId · SK: ts — agentId, action, outcome, confidence, citations[]. 

DocumentDB — CarePackage (formatted, versioned member communication payloads) — versioned per member per stage; PHI-bearing, encrypted, authenticated-channel only. 

DynamoDB — SignalJourneyMap — CPT/signal → journey (+entry stage) mapping and computed scores used by the Signal Analyzer in the On-Demand flow. Thin, key-addressable state only — no raw clinical/claims data. (A graph DB such as Neptune is not required for the pilot: the mapping is small and looked up by key; if a rich relationship graph is ever needed it can be reintroduced via DIL.) 

S3 — Skill Registry — versioned per-journey/per-stage skill files, loaded by the orchestrator at runtime. 

OpenSearch — vector store for retrieval used by agents (non-PHI embeddings / content). 

 

Low Level Design — Approach 1 [Recommended]: Serverless Cohort-Batch Orchestration 

Thesis: the primary mode is Cohort Batch at millions scale on a daily cadence. The Journey Executor is realized on AWS Lambda — triggered by Experience Studio (JES) publishing a journey, it stores the journey definition in DynamoDB, calls the DIL API once per journey (async, no data retrieval), and exits. DIL writes the stage cohorts to S3 asynchronously, then writes a journey-level complete.json when all stages are done. An EventBridge S3 rule on that file triggers the Stage Reconciliation Glue (Spark) job, which produces one clean record per member, writes to Member DB (DynamoDB), and publishes one SQS message per member as a final step using foreachPartition. EKS agent pods consume from SQS directly — no Lambda, no EventBridge in the agent trigger path — with KEDA scaling pod count to queue depth. This clean separation — Lambda for API orchestration, Glue for data reconciliation + fan-out, EKS for inference — keeps each component doing what it does best with no Step Functions in the path. 

Per-component AWS service mapping (Approach 1) 

Component 

AWS service (Approach 1) 

Why 

C1 Signal/cohort ingestion 

S3 event (DIL cohort delivery) + EventBridge rules (On-Demand signals) 

S3 event fires stage-completion trigger when DIL delivers cohort files; EventBridge routes On-Demand signals directly. 

C2 Journey Executor 

AWS Lambda, triggered by Experience Studio (JES) publish event 

Receives journey definition from JES; stores it in DynamoDB JourneyDefinitions; then calls DIL API once per journey with KBQ + S3 prefix. Does no data retrieval — DIL handles the stage breakdown internally and writes to S3 asynchronously. 

C2b Stage Reconciliation 

AWS Glue (Spark), triggered by EventBridge S3 object created rule (journey-level complete.json) 

Reads all stage cohorts for the journey; deduplicates to one row per member with highest/latest stage; writes to Member DB (DynamoDB). Spark is right here because this is a large distributed data join/dedup over cohort files. 

C2c Journey-completion trigger 

EventBridge S3 object created rule on s3://{journeyId}/complete.json 

DIL writes s3://{journeyId}/complete.json when ALL stages are done. EventBridge watches for this file and triggers the Glue job directly — no tracking record needed. Open issue #2 resolved. 

C3 DIL integration 

PrivateLink to DIL AgentCore Gateway (MCP/A2A) 

Governed, private, no PHI copy. 

C4 Journey Executor Agent 

EKS pods consuming from SQS member-agent-queue — KEDA ScaledObject scales pods on ApproximateNumberOfMessagesVisible 

Glue publishes one SQS message per reconciled member (foreachPartition + boto3 batch). EKS pods long-poll SQS directly — no Lambda bridge. KEDA scales pod count precisely to queue depth; pods scale to zero when queue is empty. 

C5 Specialized agents/sub-agents 

Bedrock AgentCore + Lambda tool adapters 

Claude Sonnet 4.5 + Guardrails; Lambda for deterministic tool calls. 

C6 Communications + ATC gate 

Lambda + MSK (atc-request / atc-response topics) + ATC 

Lambda formats payload; Step Functions publishes ATC request to MSK and waits for callback (async .waitForTaskToken); ATC consumes and responds via MSK response topic. 

C7 Channel Adapter 

MSK topic → channel consumers; payload in DocumentDB 

Decoupled, durable hand-off to Sydney/SMS/Email. 

C8 State stores 

DynamoDB, DocumentDB, S3, OpenSearch, Secrets Manager, KMS 

Managed, multi-AZ, encrypted. 

C9 Command Center feed 

DynamoDB Streams → OpenSearch/QuickSight 

Real-time metadata surface without touching prod data. 

Cadence / retry 

JES publish event → Lambda (Journey Executor) + SQS DLQ for failed members 

JES publishing a journey triggers Lambda immediately; Lambda stores the definition and calls DIL. SQS DLQ captures failed member invocations for replay. 

Observability 

CloudWatch (logs/metrics/alarms/traces), correlation by sessionId 

Per-agent-turn tracing. 

Component 1 — Journey Executor (Lambda) + Stage Reconciliation (Glue) 

Diagram — Cohort Batch flow (sequence): 

 

Description. Experience Studio (JES) triggers the Journey Executor Lambda when a journey is published. The Lambda receives the journey definition payload from JES, stores it in DynamoDB (JourneyDefinitions), then immediately extracts the journey KBQ and calls the DIL API once per journey — passing two parameters: (1) the journey-level KBQ, (2) the S3 prefix s3://{journeyId}/. The Lambda does no data retrieval, no processing — it simply stores the definition and fires the API call, then exits. DIL is asynchronous, handles the stage breakdown internally, and writes all stage cohorts to S3. 

DIL contract — what DIL writes to S3 per stage: 

s3://{journeyId}/{stageId}/cohort           ← member IDs + columns (e.g. DOB) — format TBD (JSON or Parquet) 

s3://{journeyId}/{stageId}/header           ← column names and metadata for the cohort file 

s3://{journeyId}/{stageId}/complete.json    ← per-stage write-complete signal; data may be mid-write without this 

  

When all stages are complete, DIL writes a journey-level signal: 

s3://{journeyId}/complete.json              ← journey-level signal; DIL writes this when ALL stages are done 

  

Stage-completion trigger — resolved. An EventBridge S3 object created rule watches for the journey-level complete.json at s3://{journeyId}/complete.json. When DIL writes this file, EventBridge fires and triggers the Stage Reconciliation Glue job directly. Open issue #2 is resolved — no tracking record or polling logic is needed; DIL itself explicitly signals when all stages are complete. 

Stage Reconciliation (Glue/Spark). Once triggered, the Glue job reads all stage cohorts for the journey from S3. A member can appear in multiple stage cohorts (e.g. stages 1, 2, and 3). The Glue job produces one row per member per journey with a single final reconciled stage (highest/latest stage wins — timestamps are one factor). This clean record is written to Member DB (MemberJourneyTable). Glue is the right tool here because this is a large distributed data join/dedup — exactly Spark's strength. 

Agent trigger — Glue → SQS → EKS pods (KEDA). After writing to Member DB, Glue publishes one SQS message per reconciled member as a final step using foreachPartition + boto3 batch (SQS batch limit = 10 messages per API call, distributed across Spark executors). Each message is self-contained — it carries all 3 core fields plus supporting metadata, so the EKS pod never needs to re-read DynamoDB for its input. EKS agent pods consume from SQS directly using long-polling; KEDA scales pod count based on ApproximateNumberOfMessagesVisible — pods scale up as the queue fills, scale to zero when empty. No Lambda, no EventBridge in the agent trigger path. 

Per-member technical flow — exact step order: 

Experience Studio (JES) publishes a journey → triggers Journey Executor Lambda 

Lambda stores the journey definition in DynamoDB (JourneyDefinitions) 

Lambda extracts the journey KBQ → calls DIL API once per journey — passes KBQ + S3 prefix s3://{journeyId}/ — then exits (no wait) 

DIL writes cohort file + header file + complete.json to s3://{journeyId}/{stageId}/ for each stage 

DIL writes s3://{journeyId}/complete.json when all stages are done 

EventBridge S3 rule detects the journey-level complete.json → triggers Stage Reconciliation Glue job 

Glue reads all stage cohorts → deduplicates → writes one row per member to Member DB (DynamoDB) 

Glue publishes one SQS message per member to member-agent-queue (foreachPartition — parallelised across Spark executors) 

EKS agent pod long-polls SQS, consumes message (memberId, journeyId, reconciledStage, skillFileS3Uri, cycleId, sessionId) 

Pod loads skill file from S3, runs Bedrock inference, decides NBA 

Pod writes NBA result + nextStage to Member DB (DynamoDB) 

Pod deletes message from SQS (acknowledges completion) 

Pod dispatches to channels (pinned — future scope; communication channel contract TBD) 

Scope for pilot (MSK journey): one Journey Executor Lambda, one Stage Reconciliation Glue job, MSK journey only. One Glue job per journey vs one for all journeys is an open decision (open issue #1) — deferred until after pilot with pros/cons based on concrete data. 

Trigger model. The Journey Executor Lambda is triggered by Experience Studio publishing a journey. The cycle runs immediately on publish — the journey definition is stored to DynamoDB and DIL is called in the same Lambda invocation. For sub-hour reactivity on individual signals, use the On-Demand path (EventBridge → Signal Analyzer Lambda) — that path reacts without touching the batch cycle. 

Scale — Processing Millions of Members 

Cohort scale is a first-class design constraint. The new architecture naturally distributes scale across three components with different scaling models. 

Journey Executor Lambda — API Fan-Out 

The Lambda calls the DIL API once per journey — one API call regardless of how many stages the journey has. DIL handles the stage breakdown internally. Lambda scales instantly and the total execution time is bounded by the DIL API latency (not the cohort size or stage count). 

Lambda calls:  1 API call per journey 

               3 journeys = 3 DIL API calls total 

               Lambda exits after firing — no wait 

  

Stage Reconciliation Glue — Data Processing at Scale 

Glue (Spark) handles the data-heavy work: reading potentially millions of member rows across all stage cohorts and deduplicating to one row per member. Glue is the right tool because this is a distributed data join. 

DIL delivers:  s3://{journeyId}/{stageId}/cohort           ← stage cohort file 

               s3://{journeyId}/{stageId}/header           ← schema metadata 

               s3://{journeyId}/{stageId}/complete.json    ← per-stage write-complete signal 

               s3://{journeyId}/complete.json              ← journey-level signal (all stages done) 

  

Glue reads → deduplicates → writes: 

               DynamoDB MemberJourneyTable — one row per member, reconciledStage 

  

DPU Sizing Guide (Stage Reconciliation) 

Cohort Size 

Recommended DPUs 

Est. Glue Time 

Notes 

100K members 

5 DPUs 

~3 min 

Pilot / MSK journey 

1M members 

20 DPUs 

~8 min 

Single large journey 

5M members 

50 DPUs 

~15 min 

Population-scale 

10M members 

100 DPUs 

~25 min 

Max scale — confirm S3 cohort file delivery at this size 

DPU count is a single config value on the Glue job — no code change or redeployment needed. 

Agent Fan-Out — Glue → SQS + KEDA EKS 

After writing to Member DB, Glue publishes one SQS message per member as a final foreachPartition step. Spark distributes the SQS writes across all executors in parallel using boto3 send_message_batch (10 messages per API call). EKS agent pods consume from SQS directly: 

def publish_to_sqs(partition): 

    sqs = boto3.client('sqs') 

    batch = [] 

    for row in partition: 

        batch.append({'Id': row.memberId, 'MessageBody': json.dumps(row.asDict())}) 

        if len(batch) == 10: 

            sqs.send_message_batch(QueueUrl=QUEUE_URL, Entries=batch) 

            batch = [] 

    if batch: 

        sqs.send_message_batch(QueueUrl=QUEUE_URL, Entries=batch) 

  

reconciled_df.foreachPartition(publish_to_sqs) 

  

KEDA ScaledObject watches ApproximateNumberOfMessagesVisible on the SQS queue and scales EKS pods accordingly: 

Queue fills after Glue publishes → KEDA scales pods up 

Pods drain the queue → KEDA scales pods down to zero 

Zero idle cost between cycles — pods only run when there is work 

Throughput: with N pods each processing one member at ~30s (Bedrock inference), throughput = N × 2/min members. At 500 pods: 1,000 members/min = ~83 hours for 5M members. Scale pods to 5,000 for ~8 hours. KEDA + Karpenter handle node provisioning automatically. 

Bedrock Throttle Protection 

Bedrock inference is the primary scaling bottleneck — every member needs at least one LLM call. Three mitigations applied at scale: 

Provisioned throughput — pre-buy tokens-per-minute capacity from Bedrock; prevents throttling at peak concurrency 

Confidence-gated model tiering — use Claude Haiku for simple stage routing; escalate to Claude Sonnet 4.5 only when confidence falls below threshold 

Skill file caching — loaded once per agent invocation; one S3 read per member 

Cohort Size 

Estimated Tokens 

Required Action 

100K members 

~50M tokens 

Default throughput sufficient 

1M members 

~500M tokens 

Provisioned throughput recommended 

5M+ members 

~2.5B tokens 

Provisioned throughput + model tiering required 

End-to-End Cycle Timing (5M members, 3 journeys) 

Phase 

Service 

Estimated Time 

JES publish event → Lambda stores to DynamoDB + fires DIL API calls 

Lambda (JES-triggered) 

~1–2 min 

DIL processing + S3 write (async, not in our critical path) 

DIL 

Variable — depends on DIL 

Stage Reconciliation (read S3 + dedup + write DynamoDB) 

Glue (50 DPUs) 

~15–20 min 

Glue → SQS publish + EKS pod consumption (KEDA-scaled) 

Glue (foreachPartition) + SQS + EKS + Bedrock 

~parallel after Glue finishes (scales with pod count) 

Total cycle (platform-owned portion) 

 

~20–25 min + DIL time 

The DIL processing time is outside the platform's control — it is the dominant unknown in the end-to-end timing. The platform starts timing from when DIL delivers success files. 

Component 2 — Agentic layer (Bedrock AgentCore) + ATC gate 

Diagram — On-Demand Signal flow (sequence, same agent layer, reactive path): 

 

Description. The same agentic layer serves both flows. In On-Demand mode, a signal is emitted directly to EventBridge (no MSK intake hop), which routes it via business rules to the Signal Analyzer, which consults the DynamoDB SignalJourneyMap (CPT→Journey mapping; past-signal correlation via Member Journey history) and scores strength. Above threshold it triggers the Journey Orchestrator, which routes to the stage-appropriate specialized agent exactly as in batch. Agents read all workflow logic from the journey definition at runtime (no hardcoded journey logic); thresholds live in DynamoDB and can change without redeploying agents. The Communications Agent always terminates at the ATC hard gate before any dispatch. This reactive path is realized with EventBridge + Lambda (thin, low-latency) reusing the identical AgentCore agents — so batch and on-demand share one intelligence layer, satisfying the "journey-agnostic, config-over-code" principle. MSK is used only for outbound channel dispatch (C7), not for signal intake. 

Agent Routing & Invocation — Phase 1 (EKS) and Phase 2 (AgentCore) 

Who owns routing — the EKS pod itself. The SQS message published by Glue carries reconciledStage as part of the work item. The EKS pod reads its own message, inspects reconciledStage, and routes internally to the correct inference logic. No Lambda reads DynamoDB to route — the message is self-contained. Adding a new stage means updating the pod's routing logic. 

What the agent receives from SQS. The SQS message published by Glue carries 3 core fields plus supporting metadata — the pod needs nothing else to start inference: 

Field 

Role 

Type 

memberId 

Who the member is 

Core 

journeyId 

Which journey they are on 

Core 

reconciledStage 

Where they are in the journey 

Core 

cycleId 

Which execution cycle this is 

Supporting 

skillFileS3Uri 

Pointer to skill file in S3 

Supporting 

sessionId 

Correlation ID for tracing 

Supporting 

 

Phase 1 (Current Implementation) — Agents in EKS Pods 

Invocation flow: 

Glue finishes Stage Reconciliation 

  → Glue writes reconciled records to DynamoDB (Member DB) 

  → Glue publishes one SQS message per member (foreachPartition + boto3 batch) 

    Message: {memberId, journeyId, reconciledStage, skillFileS3Uri, journeyVersion, cycleId, sessionId} 

  

SQS member-agent-queue 

  → EKS pod long-polls SQS (ReceiveMessage) 

  → KEDA ScaledObject scales pods on ApproximateNumberOfMessagesVisible 

  → Pod processes message → deletes from SQS on completion 

  

EKS pod is the SQS consumer and inference engine. It receives the self-contained SQS message, routes based on reconciledStage, loads the skill file from S3, runs Bedrock inference, writes the result back to DynamoDB, and deletes the SQS message: 

EKS pod per-member execution sequence: 

  1. Long-poll SQS — receive message (memberId, journeyId, reconciledStage, skillFileS3Uri, cycleId, sessionId) 

  2. Write CLAIMED to MemberJourneyTable (conditional — attribute_not_exists, idempotency lock) 

  3. Load skill file from S3 (skillFileS3Uri) 

  4. Run Bedrock inference → decide NBA 

  5. Update MemberJourneyTable — store NBA result + nextStage 

  6. Delete SQS message (acknowledge completion) 

  7. ATC + channel dispatch [future — pinned] 

  

SQS visibility timeout as idempotency safety net. While a pod holds a message (visibility timeout), no other pod can pick it up. If a pod crashes mid-processing, the message reappears after timeout expiry and is retried by another pod. The CLAIMED write to DynamoDB (step 2) ensures that even if the message is redelivered, the pod detects the existing CLAIMED state and skips re-processing. 

IAM requirements — Phase 1: 

Role 

Permissions 

Purpose 

Glue execution role 

sqs:SendMessage, sqs:GetQueueAttributes on member-agent-queue 

Publish member work items to SQS 

EKS Pod (IRSA service account) 

sqs:ReceiveMessage, sqs:DeleteMessage, sqs:GetQueueAttributes on member-agent-queue 

Consume + acknowledge SQS messages 

EKS Pod (IRSA service account) 

dynamodb:PutItem, UpdateItem, GetItem on MemberJourneyTable 

CLAIMED write + NBA update + idempotency check 

EKS Pod (IRSA service account) 

s3:GetObject on skill registry bucket 

Read skill file at invocation 

EKS Pod (IRSA service account) 

bedrock:InvokeModel 

Inference calls 

Advantages: 

No Lambda in the agent trigger path — Glue → SQS → EKS pod is end-to-end 

SQS message is self-contained — pod never re-reads DynamoDB for its input data 

KEDA scales pods to zero between cycles — no idle compute cost 

SQS DLQ captures failed messages after N retries — no silent loss 

Natural backpressure — pods consume at their own pace; SQS buffers the queue 

 

Phase 2 (Target, ~2 months) — Migrate to Bedrock AgentCore 

Invocation flow: 

SQS member-agent-queue (Glue publishes — same as Phase 1) 

  → Lambda (SQS Event Source Mapping — bridge required for AgentCore) 

  → Lambda calls bedrock-agentcore:InvokeAgentRuntime 

      Pre-Care   → AgentCore Runtime endpoint (stages 1–4) 

      Post-Care  → AgentCore Runtime endpoint (stages 5–6) 

      Escalation → AgentCore Runtime endpoint 

  

A Lambda bridge is required in Phase 2 — AgentCore Runtime cannot directly consume from SQS. A Lambda with an SQS Event Source Mapping reads the message and calls bedrock-agentcore:InvokeAgentRuntime. The SQS message schema (published by Glue) is identical to Phase 1 — the Lambda simply passes the message payload to AgentCore. InvokeAgentRuntime returns a streaming response which Lambda handles and writes synchronously to DynamoDB. 

The correct API is bedrock-agentcore:InvokeAgentRuntime — not bedrock:InvokeAgent, which belongs to the older Bedrock Agents service (a different product and namespace). 

How the skill file is loaded: 

Lambda passes skillFileS3Uri in the InvokeAgentRuntime payload 

AgentCore loads the skill file directly from S3 at invocation start 

AgentCore caches it for the session duration — one S3 read per member 

IAM requirements — Phase 2 (AgentCore): 

Role 

Permissions 

Purpose 

Lambda adapter execution role 

bedrock-agentcore:InvokeAgentRuntime 

Call AgentCore Runtime 

Lambda adapter execution role 

dynamodb:PutItem, UpdateItem, GetItem on MemberJourneyTable 

CLAIMED write + NBA update (same as Phase 1 — Lambda still owns these) 

AgentCore execution role 

s3:GetObject on skill registry bucket 

Read skill file at invocation 

AgentCore execution role 

bedrock:InvokeModel 

Inference calls — AgentCore does inference only, no DynamoDB access needed 

Trigger Lambda execution role 

bedrock-agentcore:InvokeAgentRuntime, `dynamodb:PutItem`, `UpdateItem`, `GetItem` on MemberJourneyTable 

Invoke AgentCore Runtime + state writes (same Lambda, different endpoint config) 

Advantages: 

Fully managed runtime — no EKS pods to operate or patch 

Built-in AgentCore Memory for cross-turn continuity 

Amazon Bedrock Guardrails (attach a guardrail identifier — not automatic) 

Native Agent Collaboration (A2A) between AgentCore agents — no extra wiring 

AgentCore Policy (Cedar-based) for fine-grained access control alongside ATC 

 

Phase 1 → Phase 2 Migration Plan 

Migration is per-agent and incremental —  Because the Lambda adapter is retained in both phases, the migration is purely a config change. 

What changes 

What stays the same 

Trigger Lambda endpoint config only (VPC Lattice URL → AgentCore Runtime URL) 

Trigger Lambda itself — stays in place ✅ 

Lambda adapter IAM: swap VPC Lattice invoke → bedrock-agentcore:InvokeAgentRuntime 

Lambda adapter itself — stays in place ✅ 

AgentCore Runtime registered (reuses same skillFileS3Uri) 

Agent input/output contract — identical ✅ 

EKS pod decommissioned per agent after validation 

Glue job — zero edits ✅ 

 

DynamoDB tables — no schema change ✅ 

Migration is low-risk: one config value change per agent, incremental, independently rollback-able. 

# Lambda adapter — only this line changes between Phase 1 and Phase 2 

AGENT_ENDPOINT = config.get("journey-executor-agent-endpoint") 

# Phase 1: "https://journey-executor.lattice.internal/invoke"              ← EKS pod 

# Phase 2: "https://agentcore-runtime.us-east-1.amazonaws.com/..."  ← AgentCore Runtime 

  

Standard Agent Input / Output Contract (both phases) 

The input and output shape is identical regardless of whether the agent runs in an EKS pod (Phase 1) or AgentCore (Phase 2). The Trigger Lambda is fully location-agnostic — it always passes the same core fields plus supporting metadata. 

// INPUT — what Lambda sends to the EKS pod via VPC Lattice 

{ 

  "memberId": "M123456",                // CORE — who the member is 

  "journeyId": "knee-surgery-v2",       // CORE — which journey they are on 

  "reconciledStage": "Pre-Surgery",     // CORE — where they are in the journey 

  "journeyVersion": "v4",               // CORE — version lock for this cycle 

  "skillFileS3Uri": "s3://platform-skills/knee-surgery/pre-surgery-v4.json", 

  "cycleId": "2026-09-16-02:00", 

  "sessionId": "sess-abc123" 

} 

  

// OUTPUT — what the EKS pod returns to Lambda 

{ 

  "memberId": "M123456", 

  "nba": "Send pre-surgery checklist + cost estimate", 

  "content": { "smsText": "...", "sydneyPageId": "..." }, 

  "confidence": 0.92, 

  "citations": ["skill-file-ref-001"], 

  "nextStage": "Pre-Surgery", 

  "sessionId": "sess-abc123" 

} 

  

Skill File Loading — Phase 1 vs Phase 2 

 

Phase 1 — EKS Pod (Current) 

Phase 2 — AgentCore (~2 months) 

Who loads the file? 

Pod application code (AWS SDK) 

AgentCore managed runtime 

When? 

At HTTP request handler start 

At InvokeAgent start 

Cache scope 

In-memory within the request 

AgentCore session 

IAM required 

EKS IRSA pod service account → S3 

AgentCore execution role → S3 

Code to write 

~5 lines (boto3 / S3 SDK) 

None 

Lambda adapter needed? 

✅ Yes 

✅ Yes (required — streaming response) 

Benefits / Drawbacks (Approach 1) 

Benefits 

Lower compute idle cost — Lambda invocations and Glue DPU-hours are consumption-based; cost runs only when DIL delivers data. The meaningful always-on costs (DocumentDB, OpenSearch, MSK, Bedrock Provisioned Throughput at 1M+ scale) are shared with Approach 2. The genuine A1 vs A2 delta is Glue DPU-hours vs EKS vCPU-hours — A1 still wins for a nightly batch workload. 

Massively scalable — Lambda scales instantly for API calls; Glue Spark scales with DPUs for reconciliation; Glue → SQS → EKS (KEDA) handles per-member agent fan-out at millions scale with natural backpressure and zero idle cost between cycles. 

Least operational burden — no clusters to patch; managed retries, DLQ, Glue checkpointing. 

One agent layer serves both flows; new journeys are config-only. 

Member-level audit via PlatformSessionData (DynamoDB) + CloudWatch Logs; per-cycle audit via Glue job history. 

Drawbacks / risks 

On-Demand latency floor is higher than an always-warm consumer (Lambda cold starts) — acceptable for "minutes" SLO, not sub-second. 

Glue/Spark has per-job startup latency — fine for batch reconciliation, not for real-time. 

Bedrock throughput is the scaling bottleneck; needs provisioned throughput + confidence-gated model tiering at peak. 

External-ownership risk: DIL (cohort delivery) and ATC (gate) are on the critical path and externally owned. 

 

Low Level Design — Approach 2 [Alternative]: Container/Streaming Orchestration (EKS + Kafka) 

Thesis: realize the runtime as long-running containerized services on Amazon EKS, with MSK (Kafka) as the backbone for both cohort chunks and real-time signals. The Journey Executor and Orchestrator run as always-warm, KEDA-autoscaled consumers. This is the natural evolution of the POT (which was event/stream-driven) and is optimized for On-Demand low latency and continuous streaming — at the cost of always-on cluster operation. It also maps most directly onto EHAP if EHAP is container-based. 

Per-component AWS service mapping (Approach 2) 

Component 

AWS service (Approach 2) 

Why 

C1 Signal/cohort ingestion 

MSK (Kafka) topics + EventBridge 

Streaming backbone; cohort chunks and signals both flow as Kafka records. 

C2 Journey Executor 

EKS scheduler deployment (CronJob) + Kafka producer chunking cohort 

Always-warm; emits per-member work as Kafka messages. 

C2b Per-member orchestration 

EKS consumer pods, KEDA autoscaling on Kafka lag 

Scales pods to consumer lag; warm = low latency. 

C3 DIL integration 

PrivateLink to DIL Gateway 

Same governed access. 

C4 Journey Executor Agent 

EKS service calling Bedrock AgentCore 

Container holds session/routing; AgentCore for inference. 

C5 Specialized agents/sub-agents 

EKS services + Bedrock 

Co-located agents; in-cluster A2A. 

C6 Communications + ATC gate 

EKS service + PrivateLink to ATC 

Warm gate calls. 

C7 Channel Adapter 

MSK topic → channel consumers; DocumentDB payloads 

Kafka-native (matches POT). 

C8 State stores 

DynamoDB, DocumentDB, S3, OpenSearch, Secrets Manager, KMS 

Same managed stores. 

C9 Command Center feed 

DynamoDB Streams / Kafka → OpenSearch/QuickSight 

Same surface. 

Scaling / resilience 

EKS + KEDA + Karpenter, Kafka consumer groups, DLQ topic 

Node autoscaling + partition parallelism. 

Observability 

CloudWatch Container Insights + ADOT traces 

Pod + agent-turn tracing. 

Component 1 — Journey Executor + Orchestrator on EKS 

Diagram — Cohort Batch flow (sequence): 

 

Description. An EKS CronJob plays the Executor role: it loads journey definitions, filters due journeys, fetches + reconciles the DIL cohort, and produces one Kafka message per member onto an MSK member-work topic (chunked by partition). A pool of Orchestrator consumer pods processes messages; KEDA scales pod count on Kafka consumer lag and Karpenter adds nodes. Each pod routes to the stage agent, calls DIL, writes idempotent state, formats, and clears the ATC gate. Because consumers are always warm, per-member latency is lower than a serverless cold start — but the cluster runs (and costs) continuously even when no cycle is active. 

Component 2 — On-Demand streaming path (native strength) 

Diagram — On-Demand Signal flow (sequence): 

 

Description. This is where Approach 2 shines: signals stream continuously through MSK to always-warm Signal Analyzer and Orchestrator pods, giving the lowest achievable latency for time-sensitive events (surgical prior-auth, discharge). The logical flow is identical to Approach 1's on-demand path — same agents, same ATC gate, same stores — but the warm consumer model removes cold-start and job-startup overhead. 

Benefits / Drawbacks (Approach 2) 

Benefits 

Lowest On-Demand latency (always-warm consumers) — best for real-time/streaming use cases. 

Kafka-native — directly continues the POT design; strong for continuous high-throughput signal streams. 

Full control over runtime (custom libraries, sidecars, in-cluster A2A); cleanest fit for EHAP if EHAP is container-based. 

Drawbacks / risks 

Always-on cost — cluster runs 24/7 even though the primary mode is a nightly batch; poor cost fit for bursty batch workloads. 

High operational burden — cluster ops, patching, node/pod autoscaling tuning, Kafka partition/consumer-group management. 

Scaling to millions in a batch window requires careful partition + KEDA tuning; back-pressure onto DIL/Bedrock still applies. 

More moving parts ⇒ larger failure surface and more to observe than the managed serverless path. 

 

Justification 

Recommendation: Approach 1 (Serverless Cohort-Batch). The SRD is explicit that the primary and near-term mode is Cohort Batch on a daily cadence at population scale, with On-Demand reserved for a minority of time-sensitive signals. Given that workload shape, Approach 1 is superior on the dimensions that matter: 

Dimension 

Approach 1 (Serverless/Glue) 

Approach 2 (EKS/Kafka) 

Winner 

Fit to primary mode (nightly batch, millions) 

Excellent — distributed fan-out, pay-per-use 

Works, but cluster idles between cycles 

A1 

Idle cost 

≈ $0 between cycles 

24/7 cluster cost 

A1 

Scale to millions 

Lambda (API calls) + Glue (reconciliation) + SQS + EKS KEDA (agent fan-out) 

Kafka partitions + KEDA (tunable) 

Tie 

On-Demand latency 

Minutes (meets SLO) 

Seconds (best) 

A2 

Operational burden 

Minimal (fully managed) 

High (cluster/Kafka ops) 

A1 

Config-over-code / journey-agnostic 

Yes 

Yes 

Tie 

EHAP alignment 

Deploy target abstracted 

Direct if EHAP=containers 

A2 

How Approach 1 meets the goals 

Functional: implements every P0 (schedule → cohort fetch → reconcile → route → agent NBA → ATC → dispatch → metadata) and the P1 On-Demand path via the same agent layer. 

Non-functional — scale: Glue + Distributed Map fan out to millions within the nightly window; 2×/3× is added parallelism, not re-architecture. 

Non-functional — cost (IMR / monthly AWS estimate, indicative, to be baselined): dominant cost is Bedrock inference (per member × stages × tokens), then Glue DPU-hours for the nightly fan-out, then DIL calls and DynamoDB. Because compute is consumption-based, monthly cost ≈ (cohort size × cycles × per-member inference cost) + Glue DPU-hours, with near-zero idle — materially cheaper than a 24/7 EKS cluster for a nightly workload. (Populate with rate-card numbers once cohort size and token counts are baselined in Milestone 1.) 

Non-functional — resilience/security: managed retry/DLQ/checkpointing; idempotent Member Journey Table; PHI stays in source systems; thin platform state; ATC hard gate; KMS + VPC + least-privilege IAM. 

When to revisit Approach 2: if On-Demand becomes the dominant mode (sub-minute SLOs at high sustained volume) or EHAP mandates a container runtime, migrate the warm path to EKS while keeping the batch fan-out serverless (the two can coexist — On-Demand on EKS, batch on Glue). 

Change Management 

1. Financial considerations 

Data growth: the platform stores metadata/state only (Member Journey, Session, Journey Defs in DynamoDB; care packages in DocumentDB) — not raw PHI. Growth scales with cohort size × cycles, expected modest (<5% of any source-system footprint). If per-cycle trace volume grows >5%, mitigate with DynamoDB TTL on session traces + S3 archival. 

Capacity 1yr/2yr: driven by cohort size and #journeys. 1yr: pilot cohorts (MSK + Friction) — low DPU/inference. 2yr: enterprise journeys — scale Glue DPUs and Bedrock provisioned throughput linearly; DynamoDB on-demand capacity absorbs growth. 

Per-system added cost: Bedrock (largest), Glue, DynamoDB, DocumentDB, MSK, OpenSearch, Secrets Manager, CloudWatch — itemize on the rate card post-baseline. 

2. Documents/processes to update: Monitoring runbooks (new CloudWatch dashboards/alarms), SOPs (batch-cycle operations, DLQ replay, ATC-suppression review), and system documentation (this LLD, DIL KBQ contract, ATC integration spec). 

3. Backfill: none of raw data (PHI stays in source). Initial journey enrollment/state is seeded from DIL cohorts on first cycle — no separate HW. 

4. Additional HW: none physical; all AWS-managed/consumption-based (or an EKS cluster in Approach 2). 

5. Rollback: journey definitions are versioned — revert to prior published version; disable a journey via status flag (no deploy). Runtime deploys are blue/green; Glue job and Lambda versions are pinned per cycle. 

6. Data migration: none for PHI. Platform state (DynamoDB/DocumentDB) migrates via standard export/import if needed. 

7. Peak scaling: batch spreads load across the nightly window; for peak events (e.g., large new cohort), raise Glue max DPUs / Distributed Map concurrency and Bedrock provisioned throughput; DIL/Bedrock backpressure via throttling + checkpointed resume. 

 

Appendix 

Terminology 

Journey · Signal · Cohort · Friction point · Stage · NBA · ATC · DIL · MCP · A2A · Skill file · MSK (MSK Clinical = musculoskeletal pilot; Amazon MSK = Managed Streaming for Kafka — disambiguate in prose) · DPU (Glue Data Processing Unit) · KEDA (Kubernetes Event-Driven Autoscaling) · EHAP (elegance Health Agentic Platform). 

Operational Excellence — Metrics 

Business: call-volume reduction %, avoidable repeat-contact reduction %, cost-of-care per episode, NPS/CSAT on orchestrated journeys, self-service success %. Technical: cohort throughput (members/cycle), per-member latency (batch & on-demand), grounding rate & confidence distribution, escalation/graceful-exit rate, ATC approval vs suppression rate (+reason breakdown), DIL/Bedrock call latency & throttle rate, per-stage completion rate, duplicate-send rate (target 0), cycle success/resume rate. 

Operational Excellence — Alarms 

Cohort cycle did not complete within nightly window. 

DLQ depth > threshold (per-member failures). 

DIL or Bedrock error/throttle rate > threshold. 

ATC gate call failure (fail-closed: no send on gate error). 

Duplicate-send detected (idempotency breach). 

Grounding/confidence below pilot bar (≥90% retrieval, >80% completeness). 

DynamoDB/DocumentDB/OpenSearch throttling or AZ failover. 

Launch Plan 

Feature-gate journeys via journey status and cohort-size caps (canary on a small cohort → expand). Roll out MSK Clinical Pilot first (vertical depth), then Member Friction (horizontal breadth), then Site of Care / Prior Auth Scaling. Use weblab-style A/B on outreach content (framework is future scope). Milestones: M1 Foundation & Design (Aug 10–Sep 23), M2 Platform Operational & First Use Cases Live (Sep 28–Nov 11), M3 Scale/Expand/Harden (Nov 16–Dec 30). 

Development Plan (high-level task breakdown — attach SIMs/estimates) 

DIL KBQ contract + mocked DIL for runtime dev. 

Journey Definition schema + DynamoDB tables + Experience Studio publish→Journey DB path. 

Journey Executor Lambda (triggered by JES publish event) + store journey definition to DynamoDB + DIL API call per journey. 

Stage Reconciliation Glue job + Glue foreachPartition → SQS publish + KEDA ScaledObject on EKS + SQS DLQ. 

AgentCore Orchestrator + specialized agents + skill-file loader (S3). 

Communications Agent + ATC integration (Permission-to-Send) + Channel Adapter (MSK→DocumentDB). 

Command Center feed (DynamoDB Streams → OpenSearch/QuickSight). 

Observability (CloudWatch dashboards/alarms/traces), security review (HIPAA), load test to millions. 

On-Demand path (EventBridge→Lambda→AgentCore) for time-sensitive signals. 

Future Work 

Automated journey optimization / closed-loop learning (post-2026); additional journeys (hip, cardiac rehab); cross-channel context persistence; NBA integration; migrate On-Demand to warm EKS if sub-minute SLOs emerge; Semantic Layer as a first-class DIL source. 

References 

Solution Recommendation Document (SRD) v2, Aug 2026 — §7 Solution Architecture (Cohort Batch execution model, Agentic Layer, Experience Studio). 

CIO Data Intelligence Workshop Readout (May 2026) — pilot agent decomposition. 

Architecture diagram: member-orch-aws-architecture.drawio (repo root) — interactive lightbox. 

Platform HLD: docs/high-level-design.md; steering: .kiro/steering/ (harness, tech, product, agentic-patterns). 

# Event Ledger

[![CI](https://github.com/rbalusup/event-ledger/actions/workflows/ci.yml/badge.svg)](https://github.com/rbalusup/event-ledger/actions/workflows/ci.yml)

Two independent Spring Boot microservices that process financial transaction events: an
**Event Gateway** (public-facing) and an **Account Service** (internal, called only by the
Gateway).

## Architecture

```
                       ┌──────────────────────┐
Client ───────────────▶│  Event Gateway API    │  :8080
                       │  (public-facing)      │
                       └──────────┬────────────┘
                                  │ REST (sync, via RestTemplate)
                                  ▼
                       ┌──────────────────────┐
                       │  Account Service      │  :8081
                       │  (internal)           │
                       └──────────────────────┘
```

- **Event Gateway** (`event-gateway/`) receives transaction events, validates input,
  enforces idempotency by `eventId`, stores its own event records in an H2 in-memory
  database, and calls the Account Service to apply the transaction. It wraps that call
  in a Resilience4j circuit breaker so a failing Account Service doesn't hang or take
  down the Gateway.
- **Account Service** (`account-service/`) owns account balances and transaction history
  in its own H2 in-memory database. It is never exposed to external clients directly —
  only the Gateway calls it.

The two services share no database and no in-process state. They are independent Gradle
projects, each with its own Gradle wrapper, and can be built and run entirely on their own.

## Prerequisites

- Java 21
- Docker + Docker Compose (for the primary way of running both services) — no local
  Gradle install needed either way, since both projects use the Gradle wrapper.

## Running via Docker Compose (recommended)

```bash
docker compose up --build
```

This starts both services with health checks; the Gateway waits for the Account
Service to report healthy before starting. Once up:

```bash
# Submit a transaction event
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z"
  }'

# Get a single event by id
curl http://localhost:8080/events/evt-001

# List events for an account, in chronological order
curl "http://localhost:8080/events?account=acct-123"

# Gateway health check
curl http://localhost:8080/health

# Account balance (served directly by the Account Service)
curl http://localhost:8081/accounts/acct-123/balance

# Account details + recent transactions
curl http://localhost:8081/accounts/acct-123

# Account Service health check
curl http://localhost:8081/health
```

Resubmitting the same `eventId` returns the original event with `"duplicate": true` and
`200 OK` instead of creating a second record or changing the balance.

## Running manually (without Docker)

In one terminal:

```bash
cd account-service
./gradlew bootRun          # listens on :8081
```

In another terminal:

```bash
cd event-gateway
./gradlew bootRun          # listens on :8080, calls http://localhost:8081 by default
```

To point the Gateway at an Account Service running somewhere else, set
`ACCOUNT_SERVICE_URL` (e.g. `ACCOUNT_SERVICE_URL=http://localhost:9090 ./gradlew bootRun`).

## Running the tests

```bash
cd account-service && ./gradlew test
cd event-gateway && ./gradlew test
```

| Test class | Covers |
|---|---|
| `account-service` `AccountServiceTest` | Balance computation (CREDIT − DEBIT), order-independent balance and transaction listing regardless of arrival order, idempotent apply-transaction by `eventId`, 404 on unknown account |
| `account-service` `AccountServiceConcurrencyTest` | Several threads applying the same `eventId` simultaneously still apply exactly once, with a correct balance and no unhandled exceptions |
| `account-service` `AccountControllerValidationTest` | 400 on missing/invalid fields, 404 mapping |
| `account-service` `TraceIdFilterTest` | Trace ID is generated when absent, preserved unchanged when supplied |
| `event-gateway` `EventServiceTest` | Idempotent event storage, chronological listing regardless of arrival order, metadata round-trip, **no local persistence when the Account Service call fails** |
| `event-gateway` `EventControllerValidationTest` | 400 on missing/invalid fields, 404 mapping, **actual HTTP 503 (not just the exception type) when the Account Service is unavailable** |
| `event-gateway` `AccountServiceClientCircuitBreakerTest` | Circuit breaker opens after repeated failures and fails fast (no calls reach the downstream while open), recovers to closed once calls succeed again, and a slow-but-up downstream times out via the RestTemplate read timeout before the breaker ever trips |
| `event-gateway` `TraceIdPropagationTest` | `X-Trace-Id` is generated or passed through, and forwarded to the Account Service on the outbound call |
| `event-gateway` `GracefulDegradationTest` | Full flow through real HTTP: once the Account Service is unreachable, `POST /events` fails fast with 503 and persists nothing locally, while `GET /events/{id}` and `GET /events?account=` keep working off the Gateway's own data |
| `event-gateway` `EventGatewayEndToEndIT` | Full real-HTTP flow: submit → balance updated on a real Account Service instance → resubmit is idempotent |

## API reference

### Event Gateway (`:8080`)

| Method | Path | Notes |
|---|---|---|
| `POST` | `/events` | `201` on new event, `200` + `"duplicate": true` on resubmission, `400` on invalid input, `503` if the Account Service is unavailable |
| `GET` | `/events/{id}` | `200`, or `404` if not found |
| `GET` | `/events?account={accountId}` | Events for the account, ordered by `eventTimestamp` ascending |
| `GET` | `/health` | Service status + H2 connectivity |

### Account Service (`:8081`)

| Method | Path | Notes |
|---|---|---|
| `POST` | `/accounts/{accountId}/transactions` | `201` on new transaction, `200` + `"alreadyApplied": true` on resubmission of the same `eventId` |
| `GET` | `/accounts/{accountId}/balance` | `200`, or `404` if the account has no transactions |
| `GET` | `/accounts/{accountId}` | Balance + full transaction history, ordered by `eventTimestamp` ascending |
| `GET` | `/health` | Service status + H2 connectivity |

### OpenAPI / Swagger UI

Each service exposes a live OpenAPI 3 spec, generated from the controllers via
springdoc, once running:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html` (Gateway), `http://localhost:8081/swagger-ui/index.html` (Account Service)
- Raw spec (JSON): `http://localhost:8080/v3/api-docs`, `http://localhost:8081/v3/api-docs`

Each service directory also has a `requests.http` file with a ready-to-run example
of every endpoint (happy path, idempotent resubmission, validation errors, 404s) —
usable directly with the VS Code "REST Client" extension or IntelliJ's built-in HTTP
Client, no Postman collection needed.

## Resiliency: why a circuit breaker

The Gateway wraps its call to the Account Service in a Resilience4j circuit breaker
(`event-gateway/src/main/resources/application.yml`, instance `accountService`):
count-based sliding window of 10 calls, minimum 5 calls before evaluating, 50% failure
threshold, 10s wait in the open state, 3 trial calls in half-open.

A circuit breaker was chosen over plain retry-with-backoff because the failure mode this
system most needs to guard against is a **sustained** Account Service outage, not an
occasional blip. Retrying during a real outage just adds load and delay for every caller;
failing fast after a threshold of failures gives clients an immediate, honest `503`
instead of a slow one, and stops hammering a downstream that's already struggling. The
Gateway's `RestTemplate` also has explicit 2s connect/read timeouts — a circuit breaker
alone doesn't help if the underlying HTTP client call is left free to hang.

## Tracing & observability: why manual trace IDs over full OpenTelemetry

Both services generate or accept an `X-Trace-Id` header per request (a servlet filter,
`TraceIdFilter`), store it in SLF4J's MDC for the lifetime of the request, log a
received/completed line carrying it, and echo it back on the response. The Gateway's
`RestTemplate` has an interceptor that forwards the same trace ID on its call to the
Account Service, so a single client request is traceable end-to-end across both
services' logs by grepping for one ID.

This is deliberately lighter than the full OpenTelemetry SDK: at two services and no
collector/exporter infrastructure to stand up, a manual header + MDC gives the same
practical traceability the assignment asks for (generate → propagate → log on both
sides) without the setup and dependency surface of a full tracing stack.

Both services log structured JSON (via `logstash-logback-encoder`): `@timestamp`,
`level`, `message`, `service`, and `traceId` (via MDC) on every line. Each service also
exposes one custom Micrometer counter — `gateway.events.submitted` and
`account.transactions.applied`, both tagged by outcome — via `/actuator/metrics/{name}`
and, in Prometheus text format, `/actuator/prometheus` (e.g.
`http://localhost:8080/actuator/prometheus`, `http://localhost:8081/actuator/prometheus`).
There's no Prometheus server or Grafana dashboard wired up in this repo — the endpoint
is there to be scraped by one if you point one at it.

## Known limitations / out of scope

- No Grafana, Jaeger/Zipkin, rate limiting, async fallback queueing, or contract tests —
  these were explicitly listed as bonus/optional in the assignment and were left out to
  keep the core solid within the time budget.
- Balance/currency handling assumes a single currency per account (taken from the first
  transaction); the assignment's payload doesn't describe multi-currency accounts.
- The Gateway persists its own `Event` record only *after* the Account Service call
  succeeds, so a failed call never leaves the Gateway in an inconsistent state — but if
  the Account Service applies a transaction and the response is then lost to a network
  blip, a client retry with the same `eventId` is what makes the system consistent again
  (the Account Service's `event_id` unique constraint makes that retry a safe no-op).
  This is "safe to retry," not a distributed transaction — there's no two-phase commit
  between the two services' databases.
