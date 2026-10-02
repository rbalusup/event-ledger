Agents Workflow 

Overview 

The agentic layer will occur as part of the overall runtime layer. We will focus on the cohort batch approach. Therefore, the agents will take as an input for each execution flow: the member id, the journey, and the specific journey stage, and member specific information from DIL (dynamic based on the KBQ from pre-agentic runtime layer). For example, for MSK, this will be:  

Member ID = 1234 

Journey = Musculoskeletal  

Journey Stage = Conservative Treatment, stage 3 

Member-specific Information [straight from DIL health OS] = {coverage_status: ....} 

The agents will perform the following actions. Details will be included below for each agent:  

Obtain relevant skill file documents [journey definition and journey stage separate skill files] based on the member’s journey and journey stage listed above. One agent will create a rules file, specific to a journey and journey stage.  

Another agent will execute a set of actions based on the generated rules file. This will execute domain agents, MCPs, and other touchpoints relevant to the actions required, or utilize the member-specific information input from the pre-agentic layer. [In scope for MSK: utilize member-specific information, calling domain agents/MCPs should not be required] 

Call the channel adaptor layer which will store outputs to all actions in Document DB. Then send the communications channel identifier to the ATC layer, and finally if approved, send an SMS, email, or Sydney push to the user.  

Based on the discussions from E Health, the agents should:  

Have evaluation checks before sending any outputs outside the agentic layer.  

Be designed such that adding new journey specific capabilities to the agent can be done in a  low-code/no-code format. This means that when an additional journey is added to the system, it should not require developing another agent but utilize the current agent workflow, with only updates to skill files and other documents.  Code updates should not be required.  

[To be determined] Be portable to Amazon Bedrock Agent Core, and not specific to EKS. This can be done using EHAP.  

Even though the pre-agent runtime layer will obtain cohorts (groups of members) from DIL based on a KBQ, the agent layer will run independently for each individual member.  

 

Multi-Agents Deep Dive 	 				Figure 1: Journey Rules Engine Agent Flow 

Steps (experience studio layer):  

Author interacts with the experience studio UI 

JES automatically creates Journey Definition Skill File 

JES automatically creates Journey Stage Skill File 

Once the Journey Stage skill file is created, it triggers the Journey Rules Engine Agent to create the JDM 

Journey Rules Engine Agent ingests domain agents + data sources catalog 

Map relevant actions to existing touchpoints.  Add suppression rules to each action. The Journey Rules Engine agent should determine what member specific information is required for this rule file.  

Member specific information first comes from the DIL Health OS output as a part of the pre-agentic layer.  

If certain information required for the suppression rules is missing, the agent should call domain agents/MCPs that are relevant (ex: EDP, Benefits, FindCare, etc..).  [Phase 1 out of scope, will be testing in phase 2] 

[Future Use Cases] Actions that require additional member information determined by the benefits agent, findcare, EDP, etc.. will require these calls as a part of the rules file.  

For example, does an action require to send a sms message, email, sydney care package etc.., and what information if any is required (DIL member information inputs, benefits, find care, etc..). Also, add relevant suppression rules as stated in the skill file (phase 1 scope). If action capability doesn’t exist currently, propose alternate action (phase 2 scope) 

Journey Rules Engine agent creates GoRules JDM file 

Store skill files + GoRules JDM file in DynamoDB 

 

 

				Figure 2: Agents in Runtime Layer 

Steps (agent runtime):  

Pass individual member input (member id + journey + journey stage + member information pertaining to journey KBQ) to Journey Executor Agent.  

Journey Executor Agent obtains the GoRules JDM file from DynamoDB based on the journey and journey stage of the member. The journey executor agent reads the rules file, determines required member information required for suppression, and either uses the member specific information from journey KBQ or calls EDP. Finally, it runs the rules file using a zen engine.  

The agent executes the rule file by sending the instructions back to the agent. The agent will run the necessary tool based on the steps from the rule file. For example, it will let the agent know whether to execute a suppression rule, call a domain agent/mcp such as benefits or findCare, or call the communications agent to send an SMS.  This ensures that the sequence of steps is deterministic, but the execution of the specific step utilizes reasoning.  

Journey Executor organizes all required information from executed actions, passes an eval check using AEF, then sends it to the communications agent. The Journey Executor also sends the preferred channel for the user (EDP contact preferences + Next Best Channel Engine).  

Select template, create templated message and sends to channel adaptor (which stores into Document DB), passes final AEF eval check. Finally, call ATC, and if approved, send an SMS, email, or Sydney push to the user.   

Go Rules JDM is authored in the Experience Studio Layer. This layer executes it.  

AEF runs INLINE: each agent is evaluated on completion; only a passing result advances to the next layer.  

 

Journey Rules Engine Agent 

This agent is run as a part of the experience studio layer, during the process when a user creates the journey stage skill file. The main role of the journey rules engine agent is to create a Go Rules Engine JSON/JDM file (look under the “Go Rules Engine JSON File” subsection) specific to a version of the journey stage skill file. Since it is not specific to a member, this agent will lie outside the main runtime layer and be a part of the experience studio layer.  

The user experience for a user in the experience studio utilizing the journey rules engine agent is as follows:  

User/JES will create a journey definition file as an overview of the journey. This journey definition file will then be stored in DynamoDB.  

User/JES will create the journey stage skill file.  

When the journey stage skill file creation is complete, it will trigger the journey rules engine agent to create the JDM file.  

Once done, the user can click “verify JDM file” to verify using AEF (look at “Evaluation” section below). At this point, a JDM is created for the specific version of the journey skill file.  

Under the hood, the journey rules engine agent will:  

Retrieve the relevant journey definition file and journey stage specific actions from the journey stage skill file.   

(Do we need the agent to ingest the journey definition file? It may be extra noise, but it also might have valuable background information for the journey when interpreting actions. This is an experiment we will need to run. Initially, let’s add both the journey definition and journey stage skill file into the context, and compare results with just ingesting journey stage skill file.)  

Ingest a file showcasing a brief description of the entire data sources + touchpoints catalog (including MCPs and Domain agents).  

Finally, the agent must determine how each action must be executed, based on the existing capabilities of runtime agents. This list of capabilities is listed in the catalog file in step 2 above.  

First, the agent must add all suppression rules for each action. This will be added to the rules file as conditional branches. At the same time, it should add a step to obtain member specific information to run these suppression rules. Initially, we will assume this comes from DIL from the pre-agentic runtime layer, but after phase one, downstream agents may need to call additional domain agents/MCPs to obtain the required information.  

This assumes that existing data sources will always map to an action. [Phase 2 scope] However, if it doesn’t, it should notify the user that such an action cannot be performed currently and provide alternate actions instead.  

[Phase 2 scope]  It can be ambiguous whether data sources/touchpoints exist for an action. For example, a general action such as “create care package” requires calling various data sources, but there is no “care package agent”, so the agent could incorrectly assume the capability doesn’t exist. This requires the agent to have enough context to be able to disambiguate general actions.  

If an action is too general, it can prompt the user to make an action more specific by giving options. For example, for the action, “create care package”, the agent can prompt the user for components inside a care package, including checklist, benefits information, etc.  

Finally, create a Go rules engine JSON file based on the required actions that can run a deterministic step-by-step workflow. Advantages of this deterministic workflow is as follows:  

 Running Actions Accurately: If a journey skill file includes 15+ actions, it is important to not skip any of the actions. For downstream agents (Journey executor agent), utilizing a probabilistic LLM will potentially lead to actions being skipped. Therefore, creating a deterministic JSON workflow using Go Rules will ensure that none of the actions are skipped.  

LLM Token Efficiency: For downstream agents in the runtime layer, running a deterministic workflow is more efficient than letting an LLM decide steps. Thousands of members may share a journey stage. Therefore, allowing all members to run the same deterministic workflow will lead to LLM token efficiency.  

Key Idea: The sequence of actions is deterministic through a rules file. However, when each action in the rules file is essentially an instruction sent to the agent. The journey executor agent will take in each action from the rules file and run it, utilizing its internal context and reasoning capabilities.  

 

Skill File Format 

The agent will have access to the journey-definition.[journey-name].md. For example for the MSK case, the journey definition file would be journey-definition.msk-knee-oa.md.  

 The JDM file is created simultaneously while the user is creating an associated stage file, such as stage-1-initial-presentation.md, stage-2-conservative-treatment.md, stage-3-imaging-authorization.md, etc.. 

The journey definition files should include a definition, business objective, target outcome, and a high-level overview of the stages. It is still TBD to understand whether this journey definition file is important as context for downstream agents.  

Before the agent creates a JDM file, the ongoing creation of the journey stage skill file (for example: stage-2-conservative-treatment.md) must include an overview of the stage, and an action (inputted by the user of experience studio) required for a member in that stage. For example, if a member is in the conservative treatment stage of the MSK journey, the user may add as an action, gathering evidence of previous PT visits and guiding the members on next steps.  

While the user is adding an action or actions, the journey rules engine agent will create a first version of the JDM file. Once complete, it will output a human readable flow diagram associated with the JDM file (see below sub-section “Go Rules Engine JSON File” for details), which the user will verify. This step will also include verification of action capabilities through the data sources catalog. If a suggested action can’t be done by the agentic layer, or if it's not specific enough, the agent will prompt the user with alternate options based on existing capabilities. Lastly, when the user is done, they can trigger a verification process which will verify the JDM using evaluation techniques described in the Evaluation subsection below.  

Go Rules Engine JSON File 

In GoRules (Zen Engine),  business rules and decision graphs are stored in a human-readable JSON format called the JSON Decision Model (JDM). A standard decision file exported from the GoRules Visual Editor consists of nodes (the operations, inputs, or decision tables) and edges (the lines connecting them). Below is an example of a JDM file that could be generated by the Journey Rules Engine Agent:  

{ 

"description": "MRI Prior Auth Care Package workflow for CPT 73721 knee MRI. Retrieves authorization, member info, contact preferences, benefits, in-network providers, and assembles a care package. Includes denial branch.", 

"nodes": [ 

{ 

"id": "start", 

"type": "inputNode", 

"name": "Start" 

}, 

{ 

"id": "get_prior_auth", 

"type": "functionNode", 

"name": "Get Prior Auth Status", 

"content": { 

"tool": "get_prior_auth", 

"description": "Retrieve the MRI prior authorization for CPT 73721 (lower-limb/knee MRI).", 

"instructions": "{add instructions here}", 

"context_key": "prior_auth" 

} 

}, 

{ 

"id": "auth_branch", 

"type": "switchNode", 

"name": "Auth Branch", 

"content": { 

"context_key": "auth_status" 

} 

}, 

{ 

"id": "assemble_denial_package", 

"type": "functionNode", 

"name": "Assemble Denial Package", 

"content": { 

"tool": "assemble_denial_package", 

"description": "Assemble a denial care package when prior authorization has been denied.", 

"instructions": " {add instructions here} ", 

"context_key": "care_package" 

} 

}, 

{ 

"id": "get_member", 

"type": "functionNode", 

"name": "Get Member Information", 

"content": { 

"tool": "get_member", 

"description": "Retrieve the member's identity and plan details from EDP.", 

"instructions": " {add instructions here} ", 

"context_key": "member_info" 

} 

}, 

{ 

"id": "get_member_contact_preferences", 

"type": "functionNode", 

"name": "Get Member Contact Preferences", 

"content": { 

"tool": "get_member_contact_preferences", 

"description": "Retrieve the member's contact channel preferences, state, and benefits preamble flags.", 

"instructions": " {add instructions here} ", 

"context_key": "contact_prefs" 

} 

}, 

{ 

"id": "send_to_benefits_agent", 

"type": "functionNode", 

"name": "Get Member Benefits", 

"content": { 

"tool": "send_to_benefits_agent", 

"description": "Retrieve the member's benefits for Total Knee Arthroplasty at the journey level.", 

"instructions": " {add instructions here} ", 

"context_key": "benefits" 

} 

}, 

{ 

"id": "get_provider_network", 

"type": "functionNode", 

"name": "Get In-Network Imaging Centers", 

"content": { 

"tool": "get_provider_network", 

"description": "Retrieve 3\u20135 nearby in-network imaging centers for the approved knee MRI.", 

"instructions": " {add instructions here} ", 

"context_key": "providers" 

} 

}, 

{ 

"id": "assemble_care_package", 

"type": "functionNode", 

"name": "Assemble Care Package", 

"content": { 

"tool": "assemble_care_package", 

"description": "Assemble the final MRI care package JSON for the member.", 

"instructions": "{add instructions here}\"\n }\n}", 

"context_key": "care_package" 

} 

}, 

{ 

"id": "end", 

"type": "outputNode", 

"name": "End" 

} 

], 

"edges": [ 

{ 

"id": "e1", 

"sourceId": "start", 

"targetId": "get_prior_auth" 

}, 

{ 

"id": "e2", 

"sourceId": "get_prior_auth", 

"targetId": "auth_branch" 

}, 

{ 

"id": "e3", 

"sourceId": "auth_branch", 

"targetId": "assemble_denial_package", 

"condition": "denied" 

}, 

{ 

"id": "e4", 

"sourceId": "auth_branch", 

"targetId": "get_member", 

"condition": "approved" 

}, 

{ 

"id": "e5", 

"sourceId": "auth_branch", 

"targetId": "get_member", 

"condition": "pending" 

}, 

{ 

"id": "e6", 

"sourceId": "assemble_denial_package", 

"targetId": "end" 

}, 

{ 

"id": "e7", 

"sourceId": "get_member", 

"targetId": "get_member_contact_preferences" 

}, 

{ 

"id": "e8", 

"sourceId": "get_member_contact_preferences", 

"targetId": "send_to_benefits_agent" 

}, 

{ 

"id": "e9", 

"sourceId": "send_to_benefits_agent", 

"targetId": "get_provider_network" 

}, 

{ 

"id": "e10", 

"sourceId": "get_provider_network", 

"targetId": "assemble_care_package" 

}, 

{ 

"id": "e11", 

"sourceId": "assemble_care_package", 

"targetId": "end" 

} 

] 

} 

 

The GoRules JDM file above represents a workflow as shown below:  

 

Disclaimer: The example GoRules JDM file above only shows an example of the format, not an example of the exact content of the JDM itself. For example, in the real agentic workflow, we will not need an auth branch, but otherwise another branch signifying whether a member has gone through a PT appointment.  

 

Evaluation Check 

Evaluation is necessary to quantify how well the journey rules engine agent has performed in its tasks. Listed below are the main tasks of the agent:  

Read each action from the journey specific skill files and map it to a required data source, domain agent, or touchpoint.  

[Phase 2 scope] Correctly identify whether an action is in-scope/out-of-scope based on existing capabilities.  

[Phase 2 scope] If an action is out-of-scope, providing alternative actions that can be run.  

Create a Go Rules JSON file 

Evaluation must be done on each of these components.  

For mapping each action to a required data source:  

First, a simple check should be done to make sure that the data source/touchpoint catalog is being extracted and read.  

Then, every action from the journey specific skill file chosen must be considered in the agent thinking when mapping to the data source/touchpoint catalog. Therefore, calculate completeness, making sure that 100% of the actions are considered.  

Finally, run faithfulness to make sure that none of the mapped actions to touchpoints/data sources are hallucinated. They must all come from the catalog file.  

For creating the GoRules JDM file 

Execution accuracy to validate that the generated JDM file is syntactically correct.  

Completeness to ensure that all actions are considered from the journey stage file. This includes conditional branches and actions.  

Faithfulness to ensure that no information from the JDM file is hallucinated.  

Journey Executor Agent 

The journey executor agent ingests the GoRules Engine JDM file from the Journey Rules Engine Agent (experience studio layer) and executes the workflow. To run all the actions, this agent must have connections to all the data sources (domain agents + MCPs). Each action from the JDM file will be executed using a zen engine, utilizing each of the data sources. Finally, the content required from the set of actions is organized and passed onto the communications agent.  

Data Sources 

The following data sources are required for the journey executor agent, including components required from each agent. The information populated for each data source is subject to change, due to ongoing effort by the team to understand each data source in detail.  

Claims Explainability Domain Agent (For Member Friction – Claims use cases) 

Denial reason, denial category, appeal steps, appeal deadline: When a claim is obtained, the agentic layer needs to understand the reason for a claim denial, and if the user wants to appeal, the corresponding process.  

Claims Status: Understand where the member is in their member friction journey relating to claims.  

Benefits Explainability Domain Agent 

Obtain EOB, coinsurance, deductible, out of pocket max.  

Understand PT visit cost and visit limits, such as visits per year covered and how many visits have been used. This way, benefits explanation can be more personable to a member based on the number of visits.  

Input procedure code and CPT to scope the benefits response to a specific service.  

Member Specific Information from DIL Health OS – Passed in as part of the input to the journey executor agent from the pre-agentic runtime layer. Agentic layer will NOT call this.  

Comprehensive information for a member including clinical history, engagement history, demographics, plan info, prior interactions, preferences 

Chronic condition flag for a member 

Return risk scores (clinical risk, engagement risk, churn risk, etc..).  

To obtain specific medical history information to run suppression rules on various actions. 

Find Care API 

Using member information + procedure code to obtain the nearest care options – members prefer care options and pharmacy’s that are nearby 

Obtain cost tier per facility outputted to separate expensive but necessary facilities with cheaper options 

Virtual Care available Boolean to recommend lower-cost virtual care options 

EDP Member Contact Preference + Next Best Channel 

For a specific member, obtain their specific contact preference. Next best channel is utilized to rank these contact preferences.  

Below are the following API connections for Communications agent:  

SFMC API: Outbound Email Delivery 

Sydney API: In-app delivery, personalized nudges, conversational interface 

ECOMM: In-app notifications/Sydney Push Notifications 

Liveperson SMS: Persistent in-app message/notification for all member outreach 

Air Traffic Controller (ATC): Permission-to-send clearance, consent/supression gate 

Lastly, the DIL Cohort generation touchpoint is required in the pre-agentic layer, outside the scope of the agentic layer.  

Evaluation 

There are three main tasks executed by the journey executor agent:  

Run the GoRules JDM file using a rules engine (zen).  

Invoke domain agents/MCPs per each mapped action when relevant. For phase one, this does not need to be done.  

Send all relevant content to the communications agent, including:  

Member contact preferences 

Relevant member specific information 

Showcased below is how evaluation can be done on each component:  

Run GoRules JDM file: Execution accuracy to determine syntactical errors 

Invoke domain agents/MCPs 

Completeness: Is the agent invoking all data sources required from the GoRules JDM file.  

Relevance: Does the agent have access to only the most relevant information from each of the data sources. Irrelevant information can be additional noise to the agent.  

Send Relevant Content to the Communications Agent 

Completeness: Do we consider all the actions run by this agent in the message.  

Faithfulness: The message doesn’t have any information that is hallucinated. For example, if an action wasn’t triggered, it should take this into account in the resulting message.  

Additional testing requires 4-5 example members we can test which encompasses each of the in-scope journeys: MSK, member friction – claims, and site of care. Ground truth for each of these members includes expected actions: domain agents + MCPs that must be run (and their results) and contact preferences (to be used for evaluating the communications agent). For example, if a member is asking for benefits, what is the ground truth benefits info for this member?  

Communications Agent 

The communications agent must perform the following actions:  

Obtain the preferred contact channel (contact preferences + next best channel) for the member from the journey executor agent. Understand which contact channel to use.  

Select a template for the chosen channel. The scope of channels includes email, ECOMM, LivePerson (SMS), and Sydney.  

Craft a message from the template. This message will be stored in a specific format (TBD), call the channel adaptor lambda function created by the runtime team, and then store it in Document DB.  

The communications agent first accesses ATC, then retrieves permission to send a communication push, and finally sends the push to the preferred contact channel.  

Evaluation 

Based on the above actions by the communications agent, the following evaluation metrics will be computed:  

Correctness: Do we obtain the correct template type based on the member’s contact preferences and the output of next best channel?  

Completeness: Did the communications agent consider all the key messages.  

Faithfulness: Did it hallucinate any information 

 

Questions 

Experience Studio 

The journey rules engine agent obtains skill files that are created by the experience studio layer. The following questions to address:  

One of the actions in the journey stage skill file is “determine next steps”. Will you provide more specifics on what next steps entails? Or do we want the agent to disambiguate such steps based on the data sources that it has access to?  - This is not an existing action, this was just mocked up a couple weeks ago. This will not be an action in the MSK use case.  

In the journey stage skill file, there is a section for escalation paths. If such a path is triggered, are there escalation-specific actions that need to be run? If so, we may want to add an addition escalation specific sub-agent or tool which connects to touchpoints relating to escalations. - Not in scope for phase 1, but may need to address escalation actions in phase 2.  

The JDM file created by the journey rules engine agent is not specific to a member, and instead unique to a version of a journey stage skill file. Therefore, we are thinking of moving the journey rules engine agent to the experience studio layer, so that when a journey stage skill file is ready, it will automatically create a JDM and store it in DynamoDB. Can we have a member of the agent team work on creating this for the experience studio?  - Yes, this is being done currently. The journey rules engine agent is added to the experience studio layer, and once a journey and journey stage file is authored, it triggers the journey rules engine agent to create a rules file.  

Runtime Layer (Pre/Post Agent) 

The key assumption throughout this document is that the agents will independently run through an individual member at a time. However, when a cohort of millions of members exists, this may require concurrent agent execution, which may lead to LLM request limits. How should we think about this? - Talked with the runtime team about this, we are aligned on a runtime flow which executes the agentic layer concurrently for 5 members.  

Look at the data sources section, specifically the touchpoints/data sources that the agentic layer will not ingest. Is there alignment on this? Will the runtime layer pre/post agent take these touchpoints into account? Or should they be in-scope for the agentic layer? - Understand what is in scope for the communications agent vs other components outside the agentic layer. We are aligned.  

General Questions 

Do we have a list of potential PII data? For example, we can assume all information specific to a member, their benefits, claims, etc.. are all PII, but is there a master list? - At least for now, don’t have to worry about PII data. If PII isn’t being sent through email or SMS, that should be ok for now.  

For the communication agent, are there output templates for each communications channel in scope: email, Sierra, SMS, and Sydney? - We are not sending a message to any of these communication channels. For SFMC (email) we have a set of email templates to send, but the actual content is sent using the channel adaptor, stored in Document DB, and then accessed via Sydney when a user logs in.  

Any updates on ground truth results for specific members (4-5)? - Not in scope for phase 1, may need to revisit for phase 2.  

 

Ground Truth Expectations 

Below is just an example of what a ground truth could look like. Please use this as a guide to complete for example members.  

Member 

HCID + MCID 

Journey/Procedure 

Journey Stage 

Expected Actions Run 

Expected Final Message + Contact Medium 

[Add name here] 

HCID: ... 
MCID: .... 

(ex): MCID 

(ex): Stage 2: Conservative Treatment 

(Example) 

1 ) Check previous PT appointments 

 

2 ) Create care package by identifying benefits and including a before procedure checklist 

 

....... 

Example) 

On Sydney:  

{ 

    Title: MSK conservative treatment,  

    Benefits Card: {name: deductible, amount: {add amount value}...... 

 

} 

 

Text message:  

 

“Your care package is ready, please navigate to this link [..]” 


Specialized Agents 

As of right now, specialized agents will not be necessary to complete the agentic layer. All actions can be run by accessing data sources through the journey executor agent. During the agent build, if outputs received from existing domain agents and MCPs are missing critical reasoning components, specialized agents can be defined. For example, an escalation agent may be necessary if specific actions triggered by escalation paths are required to be run, which are fundamentally different from standard actions.  

 





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
