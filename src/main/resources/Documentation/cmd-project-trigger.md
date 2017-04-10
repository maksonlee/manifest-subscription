@PLUGIN@ project-trigger
========================

NAME
----
@PLUGIN@ project-trigger - Trigger snapshot manifest update for externally updated project

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ project-trigger
  {-n/--project-name <updated project name>}
  {-b/--project-branch <updated project branch>}
  {-r/--project-hash <git commit hash that was updated to>}
  [--help]
```

ACCESS
------
Caller must be a member of the privileged 'Administrators' group

OPTIONS
-------

`-n/--project-name <updated project name>`
`-b/--project-branch <updated project branch>`
`-r/--project-hash <git commit hash that was updated to>`
: This specify the project on Gerrit that was updated outside of Gerrit
(by a pull script directly on the server, for example.)

EXAMPLE
-------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ project-trigger -n demo/project2 -b master -r 5f51acb585b6a
```

[Back to @PLUGIN@ documentation index][index]

[index]: index.html