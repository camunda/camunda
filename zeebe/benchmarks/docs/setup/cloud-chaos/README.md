# Chaos Cloud

This directory contains resources to create a serviceaccount, role and clusterrolebinding. These
have been used to create related resources in the `ultrachaos` gke. This was necessary to run our
automated chaos experiments against the camunda cloud cluster.

After the serviceaccount and related roles are created. Kubernetes will create a token for the related serviceaccount.
This token needs to be used in order to access the related kubernetes cluster.
