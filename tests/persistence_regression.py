from pathlib import Path
p=Path("app/src/main/java/com/crewalert/missedalert/AlertStore.kt").read_text()
checks=[
 'throw IllegalStateException("Pending alert storage corrupted without usable backup", e)' in p,
 '"ack_markers_corrupt"' in p,
 p.count('objectItems(c)') >= 3,
 '"items_restored_from_backup"' in p,
 '"items_corrupt_snapshot"' in p,
 '@Synchronized\n    fun incRepeats' in p,
 '?: emptySet()).toMutableSet()' in p,
 '?: emptySet()).sorted()' in p,
]
assert all(checks), checks
print("8 persistence source contracts passed; device fault injection not performed.")
