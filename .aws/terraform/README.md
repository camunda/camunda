# Overview of Terraform components

## S3
Creates an S3 bucket for storing the Terraform remote state. Only needs to be run one time.

## Global
Defines global variables or infrastructure components like networks, dns etc.

## Optimize
Main Terraform working dir. Contains the Optmize DB and EC2 instance setup.

## Modules
Custom modules to ease setting up security groups.

## Scripts
Some terraform helper scripts.


# Remote State

Each environment has its own state which is stored remotely in S3. If this is your first
time running terraform in your machine, you have to run the following command:

```bash
cd $OPTIMIZE_REPOSITORY/.aws/terraform

for project in "global optimize"; do
  terraform remote config \
      -backend=s3 \
      -backend-config="bucket=camunda-optimize-terraform" \
      -backend-config="region=eu-west-1" \
      -backend-config="encrypt=true" \
      -backend-config="key=${project}/terraform.tfstate"
done
```
This will configure terraform to use remote state in S3 for the projects.


# Resources

This examples uses the ideas from the [A Comprehensive Guide to Terraform](https://blog.gruntwork.io/a-comprehensive-guide-to-terraform-b3d32832baca#.upyyr144y)
blog post by Gruntwork.

Especially the remote state and structure ideas from [How to manage Terraform state](https://blog.gruntwork.io/how-to-manage-terraform-state-28f5697e68fa#.h53cpske7).
