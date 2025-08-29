#!/bin/bash

set -euxo pipefail

# Make sure git index is clean
git diff-index --quiet HEAD -- || \
	(echo "You have a dirty git index, please clean it"; exit 1)
test -z "$(git ls-files --exclude-standard --others)" || \
	(echo "You have untracked files, please clean your git repo"; exit 1)

# Ensure you are on zeebe-cluster
kubectx gke_zeebe-io_europe-west1-b_zeebe-cluster

# switch do main
git checkout main

# get latest changes
git fetch
git pull origin main

# switch to cw branch
git checkout medic-cw-load-tests
git pull origin medic-cw-load-tests

# update kw branch
git merge main --no-edit
git push origin medic-cw-load-tests

# create new kw image and deploy load test
./setupKWLoadTest.sh

# delete older load test
cd load-tests/setup/

cw=$(date +%V)
if [ $cw -gt 4 ]
then
  nameOfOldestLoadTest=$(ls | grep medic-y- | sort | head -n 1)
  ./deleteLoadTest.sh $nameOfOldestLoadTest

  # commit that change
  git commit -am "test(load-test): rm $nameOfOldestLoadTest"
  git push origin medic-cw-load-tests

else
  set +x
  echo -e "\e[31m!!!!!!!!!!!!!!"
  echo -e "We currently not support to delete load tests, before calendar week 5. Our deletion logic is not sophisticated enough. Please delete the load test manually."
  echo -e "!!!!!!!!!!!!!!\e[0m"
  set -x
fi


# print out the name of the new load test so it can be easily copied
nameOfNewestLoadTest=$(ls | grep medic-y- | sort | tail -n 1)
echo "Finished creating new medic load test: $nameOfNewestLoadTest"
