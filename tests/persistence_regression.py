from pathlib import Path
p=Path("app/src/main/java/com/crewalert/missedalert/AlertStore.kt").read_text()
checks=[
 'throw IllegalStateException("Pending alert storage corrupted", e)' in p,
 '"ack_markers_corrupt"' in p,
 p.count('objectItems(c)') >= 3,
 '@Synchronized\n    fun incRepeats' in p,
 '?: emptySet()).toMutableSet()' in p,
 '?: emptySet()).sorted()' in p,
]
assert all(checks), checks
print("6 persistence source contracts passed; device fault injection not performed.")
