helm upgrade --install camunda zeebe-benchmark/zeebe-benchmark \
          --namespace dd-region-0 \
          --create-namespace \
          --reuse-values \
          --set global.image.tag='dd-region-0-d8e72e0' \
          --set camunda-platform.zeebe.image.repository=gcr.io/zeebe-io/zeebe \
          --set camunda-platform.zeebe.image.tag='dd-region-0-d8e72e0' \
          --set camunda-platform.zeebe-gateway.image.repository=gcr.io/zeebe-io/zeebe \
          --set camunda-platform.zeebe-gateway.image.tag='dd-region-0-d8e72e0' \
          --set camunda-platform.zeebe.clusterSize=4 \
          --set camunda-platform.zeebe.replicationFactor=4 \
          --set camunda-platform.global.multiregion.regions=2 \
          --set camunda-platform.global.multiregion.regionId=0 \
          -f common-values.yaml
          

