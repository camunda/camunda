from camunda_orchestration_sdk import CamundaClient


def main() -> None:
    print("Hello from load-test-extract-worker!")
    with CamundaClient() as client:
      topology = client.get_topology()
      print(topology)






