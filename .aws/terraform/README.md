# Resources

This examples uses the ideas from the [A Comprehensive Guide to Terraform](https://blog.gruntwork.io/a-comprehensive-guide-to-terraform-b3d32832baca#.upyyr144y)
blog post by Gruntwork.

Especially the remote state and structure ideas from [How to manage Terraform state](https://blog.gruntwork.io/how-to-manage-terraform-state-28f5697e68fa#.h53cpske7).


# Remote State

Each environment has its own state which is stored remotely in S3. If this is your first
time running terraform in your machine, you have to run the following command:

```bash
terraform remote config \
    -backend=s3 \
    -backend-config="bucket=camunda-optimize-terraform" \
    -backend-config="region=eu-west-1" \
    -backend-config="encrypt=true" \
    -backend-config="key=(SUB_PROJECT)/terraform.tfstate"
```
This will configure terraform to use remote state in S3.


# Modules

## S3

## Global

## DB

## Scripts

## Instance
