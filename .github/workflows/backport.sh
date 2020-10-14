#!/bin/bash

# Make sure foreground process group can be terminated
trap 'echo Terminated $0; exit' INT;

# Check usage
if [ -z "$1" ] | [ -z "$2" ] | [ -z "$3" ] | [ -z "$4" ]
then
  echo "Usage: backport.sh headref baseref target branchname
  where:
    headref refers to the source branch of the merge commit, i.e. PR head
    baseref refers to the target branch of the merge commit, i.e. PR merge target
    target refers to the target to backport onto, e.g. origin/stable/0.24
    branchname is the name of the new branch containing the backport, e.g. backport-x-to-0.24
    
  exit codes:
    0: all good
    1: incorrect usage / this message
    2: unable to access worktree directory
    3: unable to create new branch
    4: unable to cd back to start"
  exit 1
fi

headref=$1
baseref=$2
target=$3
branchname=$4
worktree=".worktree/backport"

# Check that checkout location is available
if [ -d "$worktree" ]
then
  echo "This backport scripts uses $worktree as its worktree, but it already exists
  please remove it 'git worktree remove $worktree' and try again" 
  exit 2
fi

echo "Fetch latest"
git fetch

echo "
Find common ancestor between $baseref and $headref"
ancref=$(git merge-base "$baseref" "$headref")
echo "$ancref"

echo "
Find commits between common ancestor $ancref and source branch $headref"
diffrefs=$(git log "$ancref..$headref" --reverse --format="%h")
echo "$diffrefs"

echo "
Checkout $branchname"
git worktree add $worktree "$target" || exit 2
cd $worktree || exit 2
git switch --create "$branchname" || exit 3

echo "
Cherry pick commits between $ancref and $headref to $target"
echo "$diffrefs" | xargs git cherry-pick  -x

echo "
Push results to $branchname"
git push --set-upstream origin "$branchname"
cd - || exit 4

echo "
Cleanup worktree"
git worktree remove $worktree

# open "https://github.com/zeebe-io/zeebe/compare/$target...$branchname?expand=1&template=backport_template.md&title=%5BBackport%200.x%5D"

exit 0