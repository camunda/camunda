#!/usr/bin/env python3

import argparse
import random


class Index:
    def __init__(self, name):
        self.name = name
        self.process_instances = {}
        self.ilm_deadline = None
        self.ordinal = None

    @property
    def is_main(self):
      return self.name == "main"

    @property
    def is_dated(self):
      return "dated" in self.name

    @property
    def is_ordinal(self):
      return "ordinal" in self.name


class ProcessInstance:
    def __init__(self, id, end_date):
        self.id = id
        self.end_date = end_date


class Simulation:
    def __init__(self, ordinal_rollover, retention_period=30):
        self.ordinal_rollover = ordinal_rollover
        self.next_id = 1
        self.current_time = 0
        self.current_ordinal = 0
        self.retention_period = retention_period
        self.indexes = {}
        self._main_index = self._ensure_index_exists("main")
        self.indexing_operations = 0
        self.delete_operations = 0
        self.operations_cost_estimate = 0

    def new_ordinal_process_instance(self, duration):
      index = self.add_new_process_instance(f"ordinal-{self.current_ordinal:05}", duration)
      index.ordinal = self.current_ordinal

    def new_main_process_instance(self, duration):
      self.add_new_process_instance("main", duration)

    def add_new_process_instance(self, index_name, duration):
        index = self._ensure_index_exists(index_name)

        process_instance = ProcessInstance(self.next_id, self.current_time + duration)
        index.process_instances[self.next_id] = process_instance
        self.next_id += 1
        self.indexing_operations += 1
        self.operations_cost_estimate += len(index.process_instances)

        return index

    def tick(self):
        self.current_time += 1
        self._archive_process_instances()
        self._check_for_finished_ordinals()
        self._apply_ilm_policies()
        ordinal_indexes = [index for index in self.indexes.values() if index.is_ordinal]
        next_ordinal = self.ordinal_rollover.next_ordinal(ordinal_indexes, self.current_ordinal)
        if next_ordinal != self.current_ordinal:
          verbose(f"Rolling over to ordinal index: {next_ordinal} at time {self.current_time}")
        self.current_ordinal = next_ordinal

    def run_deleter(self, only_if_no_ilm_deadline=False):
      for index in self.indexes.values():
        if index.is_ordinal:
          if only_if_no_ilm_deadline and index.ilm_deadline is not None:
            continue
          for process_instance in list(index.process_instances.values()):
            if (process_instance.end_date + self.retention_period) <= self.current_time:
              del index.process_instances[process_instance.id]
              self.delete_operations += 1
              self.operations_cost_estimate += len(index.process_instances)

    def get_num_process_instances(self):
      num_processes = 0
      for index in self.indexes.values():
        num_processes += len(index.process_instances)
      return num_processes

    def get_biggest_index_size(self):
      biggest_size = 0
      for index in self.indexes.values():
        biggest_size = max(biggest_size, len(index.process_instances))
      return biggest_size

    def get_count_indexes_with_ilm_deadline(self):
      count = 0
      for index in self.indexes.values():
        if index.ilm_deadline is not None:
          count += 1
      return count

    def _ensure_index_exists(self, index_name):
      index = self.indexes.get(index_name)
      if index is None:
        index = Index(index_name)
        self.indexes[index_name] = index
      return index

    def _archive_process_instances(self):
      for process_instance in list(self._main_index.process_instances.values()):
        if process_instance.end_date <= self.current_time:
          dated_index = self._ensure_index_exists(f"dated-{process_instance.end_date:05}")
          if dated_index.ilm_deadline is None:
            dated_index.ilm_deadline = self.current_time + self.retention_period
          dated_index.process_instances[process_instance.id] = process_instance
          self.indexing_operations += 1
          self.operations_cost_estimate += len(dated_index.process_instances)
          del self._main_index.process_instances[process_instance.id]
          self.delete_operations += 1
          self.operations_cost_estimate += len(self._main_index.process_instances)

    def _check_for_finished_ordinals(self):
      for index in self.indexes.values():
        if index.is_ordinal and index.ilm_deadline is None:
          if self._check_if_index_finished(index):
            index.ilm_deadline = self.current_time + self.retention_period

    def _check_if_index_finished(self, index):
      for process_instance in index.process_instances.values():
        if process_instance.end_date > self.current_time:
          return False
      return True

    def _apply_ilm_policies(self):
      for index in list(self.indexes.values()):
        if index.ilm_deadline is not None and index.ilm_deadline <= self.current_time:
          del self.indexes[index.name]


class OrdinalRollover:
    def __init__(self, rollover_interval=1, rollover_size=1000, max_ordinals=30, circular_ordinals=False):
        self.rollover_interval = rollover_interval
        self.rollover_size = rollover_size
        self.max_ordinals = max_ordinals
        self.circular_ordinals = circular_ordinals
        self.time_since_last_rollover = 0

    def next_ordinal(self, ordinal_indexes, current_ordinal):
        self.time_since_last_rollover += 1
        if self.time_since_last_rollover >= self.rollover_interval:
            return self._rollover(ordinal_indexes, current_ordinal)
        current_index = None
        for index in ordinal_indexes:
          if index.ordinal == current_ordinal:
            current_index = index
            break
        if current_index is not None:
          if len(current_index.process_instances) >= self.rollover_size:
              return self._rollover(ordinal_indexes, current_ordinal)
        return current_ordinal

    def _rollover(self, ordinal_indexes, current_ordinal):
        self.time_since_last_rollover = 0
        if self.max_ordinals is not None and len(ordinal_indexes) >= self.max_ordinals:
          if self.circular_ordinals:
            open_ordinals = [index for index in ordinal_indexes if index.ilm_deadline is None]
            if len(open_ordinals) > 1:
              open_ordinals.sort(key=lambda index: index.ordinal)
              current_ordinal_index = -1
              for i, index in enumerate(open_ordinals):
                if index.ordinal == current_ordinal:
                  current_ordinal_index = i
                  break
              if current_ordinal_index != -1:
                next_ordinal_index = (current_ordinal_index + 1) % len(open_ordinals)
                self.time_since_last_rollover = 0
                return open_ordinals[next_ordinal_index].ordinal
          return current_ordinal
        highest_ordinal = current_ordinal
        for index in ordinal_indexes:
          if index.ordinal > highest_ordinal:
            highest_ordinal = index.ordinal
        return highest_ordinal + 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--ticks", type=int, default=100, help="Number of ticks to simulate")
    parser.add_argument("--rate", type=int, default=5, help="Number of new process instances per tick")
    parser.add_argument("--duration-min", type=int, default=10, help="Minimum of how many ticks process instances last")
    parser.add_argument("--duration-max", type=int, default=10, help="Maximum of how many ticks process instances last")
    parser.add_argument("--duration-random", choices=["uniform", "gauss"], default="uniform", help="How to randomize process instance durations")
    parser.add_argument("--retention-period", type=int, default=30, help="How many ticks finished process instances are retained before deletion")
    parser.add_argument("--mode", choices=["main", "ordinal"], default="main", help="Whether to create process instances in the main index or in ordinal indexes")
    parser.add_argument("--rollover-interval", type=int, default=1, help="How many ticks between ordinal rollovers")
    parser.add_argument("--rollover-size", type=int, default=1000, help="Rollover once the number of process instances in the current ordinal index reaches this size")
    parser.add_argument("--max-ordinals", type=int, default=None, help="Maximum number of ordinal indexes")
    parser.add_argument("--circular-ordinals", action="store_true", help="Whether to reuse ordinal indexes in a circular manner once the maximum number of ordinals is reached")
    parser.add_argument("--run-deleter", action="store_true", help="Whether to run the deleter")
    parser.add_argument("--deleter-only-if-no-ilm", action="store_true", help="Whether to run the deleter on indexes that will be dropped by ILM")
    parser.add_argument("--verbose", action="store_true", help="Whether to print detailed output")
    args = parser.parse_args()

    if args.verbose:
      verbose = print
    else:
      def verbose(*args, **kw):
        pass

    rollover = OrdinalRollover(
      rollover_interval=args.rollover_interval,
      rollover_size=args.rollover_size,
      max_ordinals=args.max_ordinals,
      circular_ordinals=args.circular_ordinals
    )
    sim = Simulation(rollover)
    if args.mode == "main":
      start_process_instance = sim.new_main_process_instance
    else:
      start_process_instance = sim.new_ordinal_process_instance

    # Simulate some process instances and time passing
    max_num_processes = 0
    biggest_index_size = 0
    for _ in range(args.ticks):
      for _ in range(args.rate):
        if args.duration_random == "uniform":
          duration = random.randint(args.duration_min, args.duration_max)
        else:
          duration = max(1, int(random.gauss((args.duration_min + args.duration_max) / 2, (args.duration_max - args.duration_min) / 6)))
        start_process_instance(duration=duration)
      sim.tick()
      if args.run_deleter:
        sim.run_deleter(only_if_no_ilm_deadline=args.deleter_only_if_no_ilm)
      max_num_processes = max(max_num_processes, sim.get_num_process_instances())
      biggest_index_size = max(biggest_index_size, sim.get_biggest_index_size())


    num_processes = 0
    for index_name in sorted(sim.indexes.keys()):
      index = sim.indexes[index_name]
      verbose(f"Index: {index_name}, Process Instances: {len(index.process_instances)}")

    print(f"Total indexes: {len(sim.indexes)}, Max process instances: {max_num_processes}")
    print(f"Indexes with ILM deadline: {sim.get_count_indexes_with_ilm_deadline()}")
    print(f"Biggest index size: {biggest_index_size}")
    print(f"Total indexing operations: {sim.indexing_operations}, Total delete operations: {sim.delete_operations}")
    print(f"Estimated total operations cost: {sim.operations_cost_estimate}")







