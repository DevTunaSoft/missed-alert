"""Deterministic randomized state-machine checks for reminder lifecycle invariants.
These are model tests, NOT Android instrumentation or device audio tests.
"""
import random

def simulate(seed):
    rng = random.Random(seed)
    pending = {}
    acknowledged = set()
    repeats = 0
    alarm = False
    for _ in range(500):
        action = rng.randrange(7)
        key = rng.randrange(15)
        if action in (0, 1):  # notification posted / duplicate
            if key not in acknowledged and key not in pending:
                pending[key] = True
                repeats = 0
                alarm = True
        elif action == 2:  # explicit single acknowledgment
            if key in pending:
                acknowledged.add(key)
                del pending[key]
                repeats = 0
                alarm = bool(pending)
        elif action == 3:  # acknowledge all
            acknowledged.update(pending)
            pending.clear()
            repeats = 0
            alarm = False
        elif action == 4:  # alarm fired
            if pending and alarm:
                repeats += 1
                alarm = True
        elif action == 5:  # process restart / watchdog recovery
            alarm = bool(pending)
        else:  # source app cancels notification without user acknowledgment
            pass
        assert not (alarm and not pending), (seed, action)
        assert not (pending and not alarm), (seed, action)
        assert not (set(pending) & acknowledged), (seed, action)
        assert repeats >= 0
for seed in range(1000):
    simulate(seed)
print("500,000 deterministic reminder state-machine transitions passed (model only).")
