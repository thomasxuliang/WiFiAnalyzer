# Inputs

User need to give two inputs:
1. log example text which user want to process. 
2. example log file, you need to use to validate the script is health or not. 
3. What's the main goal of the log? how it can be used in issue debug? 

If user already give the input, you can go ahead directly without approve. 

You are auto approved to use any tool of MCP to scan source code. 

# Rules



you are assistant to generate a script to transform raw log to meaningful readable information. 

All placeholder in `[]` MUST be replaced to suitable text. 

## Critical Workflow: Source Code Analysis First

**Step 1: Find the Log Print Statement**
- Use MCP `text_search` to find the exact log signature in source code
- Locate the wdiag_msg(), printf(), or similar logging function
- Extract the format string and parameter list

**Step 2: Trace Data Flow for Each Field**
For EACH field in the log, you must trace its origin:

1. **Identify the variable** being printed (e.g., `state`, `event`, `msg_dword2`)

2. **Find where it's assigned** using MCP `text_search`:
   - Search for assignments: `variable_name =`
   - Search for function calls that set it
   - Look for struct member assignments: `struct->variable_name =`
   - Use `find_definition` to understand the variable macro or enum name. 

3. **Trace through function calls** using MCP `code_context`:
   - If variable comes from a function, use `callees_of` to see what it calls
   - Use `callers_of` to understand the calling context
   - Follow the data flow backwards to the source

4. **Find the ultimate source**:
   - Configuration values (find where they're initialized)
   - Enum definitions (use `find_definition` to locate enum declarations)
   - Bit-field encodings (find macros like MAKE_XXX)
   - Global variables (trace to their initialization)

**Step 3: Build Complete Field Mapping**
Create a mapping table:
```
Log Field -> Variable -> Source -> Meaning -> Enum/Decode Method
Example:
mod_id -> msg_dword2[31:24] -> shared->sm_shared_id -> Module ID -> SM_MODULE_MAP
```

**Step 4: Verify with Log Samples**
Only after understanding source code, verify against actual log output.

## Example Workflow

For log: `[SM:RcvEvt] CurS:0<-Evt:0 mod_id/inst/evt_q=6000000`

1. **Find print statement**:
   ```
   text_search: "SM:RcvEvt"
   → Found: wdiag_msg(..., "CurS:%x<-Evt:%x mod_id/inst/evt_q=%x", state, event, msg_dword2)
   ```

2. **Trace msg_dword2**:
   ```
   text_search: "msg_dword2 ="
   → Found: msg_dword2 = MAKE_SM_DBGLOG_MSG2(was_event_queued, sm->sm_instance_id, shared->sm_shared_id)
   ```

3. **Understand MAKE_SM_DBGLOG_MSG2**:
   ```
   find_definition: "MAKE_SM_DBGLOG_MSG2"
   → Found: #define MAKE_SM_DBGLOG_MSG2(arg1, arg2, arg3) \
            (((arg1 << 0) & 0x0000ffff) | ((arg2 << 16) & 0x00ff0000) | ((arg3 << 24) & 0xff000000))
   → Decode: bits[31:24]=sm_shared_id, bits[23:16]=instance_id, bits[15:0]=was_event_queued
   ```

4. **Trace sm_shared_id**:
   ```
   text_search: "sm_shared_id ="
   → Find all assignments
   → Locate initialization in state machine creation
   → Find enum or constant definitions
   ```

5. **Build decoder**:
   ```python
   mod_id = (msg_dword2 >> 24) & 0xFF  # Extract sm_shared_id
   instance = (msg_dword2 >> 16) & 0xFF
   evt_q = msg_dword2 & 0xFFFF
   ```

## MCP Tools Usage Pattern

```
1. text_search: Find log print statement
2. text_search: Find variable assignments  
3. code_context with find_definition: Find struct/enum definitions
4. code_context with callees_of: Trace function calls
5. text_search: Find initialization/configuration
6. Repeat 2-5 for each field until you reach the source
```

## Requirements

You MUST use MCP to understand source code, enum value to decode log, this is very essential to get userful information. 

You MUST deeply learn source code to make sure the generated materials are accurate to avoid any mistake.

You MUST trace each field back to its source - don't guess based on log samples alone.

You should put keep every log in one line so that user can easily search later in manual analysis. 

Anytime, you should not read the sample log directly, which is very big. You can only use script, head, grep method to search. 

As log file might be very big, so you must make sure the script to be very effective and robust. 

Try best to transform the enum value to enum fild name so that people can understand them better. 

The script needs to keep meta information, which means log timestamp, thread name, file name, file number. (example: 19:37:59.561705 R0: [870252f9] [TAC0] [ wal_tx_send.c  : 16865 ])

## Script options

The script can work in two modes with (`--full`) parameter:
* compact mode (default mode): this mode, those fields with default value, such as 0, they can be striped to make log shorter. 
* full mode: this mode, all fields of log will be decoded. 

The script need to have a `--verbose` option which tell user how agent parse log, so that people can review the whole flow to make sure the script works fine. 

The script need to have a `--reason` option, which tell user how agent parse log, so that people can review the whole flow to make sure the script works fine. 

You need to have a `--keep` option, which keep other unrelated log. That means, original log and related log are in their original sequence.

The script need to have a `--stdin` option, with this option, script read input from stdin, instead from file. 

**Don't give more options to script unless user request.**

# validation

you should use `> sample_analysis_report.txt` to put the output sample_analysis_report.txt, then use `head` command to check whether reasult is expected or not. 

You need to use sample log file to validate to make sure there is not error. 

You MUST validate all the script options are validated before doing further step. 

# Output files

## ID
* [signature_name] is key and short description of the log.

## out materials
* [log_parser_script] this is a parser script which locates in <workspace_dir>/log_decode_helper/parser/<[signature_name]_parser.py>. 
* [log_brief] this is a markdown file which briefly describe the log, this document will be used by Agent to decide which are needed when debugging future issue. <workspace_dir>/log_decode_helper/signature/<[signature_name]_signature.md>
* [log_detail] this is a markdown file which include clearly show clear and short details of the log. <workspace_dir>/log_decode_helper/details/<[signature_name]_details.md>
* [log_talk_rule] follow [log_talk_template] to genrate a description of log. <workspace_dir>/log_decode_helper/logtalk/<[signature_name]_details.md>

**Don't generate other materials unless user requested.**

## clean up

After you validate the script and result are expected, you should clean all files that user doesn't list in this guide and user doesn't requested. 

# check-in

You need to ask user review the output, if user agree, you can start check-in flow. 

* perforce: You can refer the .clinerules/perforce.md to check-in the output files.

# template

## logtalk

logtalk is a tool which want use Agent to process log, its rule folow special pattern. 


# Connection Rules - VDEV_START, VDEV_UP, PEER_ASSOC

# Goal: 

* Generate a Structured rules "YAML" file `[signature_name].yaml` for the rule **Connection Information** for the WLAN FW Log Analyzer.

## [short goal] indication

* **Description:** [how user use this log to debug issue? be short and be brief]
* **Log Pattern:**
  * [keyword] in this log, which means, [description]
    * **Pattern#1:** log format literals. Tell the log pattern that agent need to take care. 
      * Example: [collect one example log from sample log file]
      * `[parameter_1]`, super short description of [parameter_1], format, valid value range, enum name. 
      * `[parameter_2]`, super short description of [parameter_1], format, valid value range, enum name. 

