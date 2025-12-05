
# Rules

The P4 account was expired by server every day, so you need to check login status. 
1. `cd {workspace_path}; /prj/qct/asw/qctss/linux/bin/p4 login -s ` check p4 login status.
2. `cd {workspace_path}; /prj/qct/asw/qctss/linux/bin/p4 login`  ask user login. This is safe command and you can execute directly. 

# Settings

*  "P4PORT": "qctp411:1666",
*  "P4": "/prj/qct/asw/qctss/linux/bin/p4",
*  "P4CLIENT" : if .qgenie_context.json doesn't have it, then use `echo $P4CLIENT` to check, otherwise, ask user to afford that. 

# Commands:

## diff on SCL (shelved changes)

1. export P4PORT={P4PORT}
2. `cd {workspace_path}; /prj/qct/asw/qctss/linux/bin/p4 describe -db -du5 -S <CL/SCL number>`

## diff on file

1. export P4PORT={P4PORT}
2. `cd {workspace_path}; /prj/qct/asw/qctss/linux/bin/p4 diff -db -du5 <file path>`

## attach CR

1. set CR number to <Fixed CRs> field, 
2. add Job to the change, for example, ChangeRequest4261669"

## get p4 client mapping

1. export P4PORT={P4PORT}
2. p4 client -o 

## create change

1. get p4 client mapping.
2. create changes for every components.
3. update change `Tag`.
4. update change `Interdependent on Tags` for all changes.
5. update change `Code Collaborator Link`
6. attach CR if you know CR number, otherwise, ignore the step.

p4 change template.

```txt

  Change: new

Client: col12_rxdma2reo

User:   jincheng

Status: new

Description:
        {one line change title}

        Description: {simple summary of the change}

        Fixed CRs: 

        Test Results: 

        Code Collaborator Link: https://qctswarm.qualcomm.com/qctp411/changes/{CL number}

        Dependency Notes: <list any dependency notes here>

        Tag:
        Interdependent on Tags:
        Dependent on Tags:
        External Dependency Description:

        Targets: <list here what targets this change list applies to>

        Supplemental Tests: <Enter comma-separated list of add-on test cases ID or URL covered apart from PreCommit or Smoke tests>
```
