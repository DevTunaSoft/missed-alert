"""Source contracts only; Android runtime and audio testing still required."""
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/crewalert/missedalert'
def read(n): return (src / (n + '.kt')).read_text()
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
assert 'minSdk = 29' in (root / 'app/build.gradle.kts').read_text()
assert any(x.attrib.get('{http://schemas.android.com/apk/res/android}name')=='.RecoveryJobService' for x in manifest.findall('.//service'))
assert 'Build.VERSION.SDK_INT < 31' in read('StatusActivity')
assert 'Build.VERSION.SDK_INT < 33' in read('StatusActivity')
assert 'watchdog_fired' in read('RecoveryJobService')
assert 'alarm_recover' in read('Scheduler')
assert 'FLAG_NO_CREATE' in read('Scheduler')
assert 'inexact_fallback' in read('Scheduler')
assert 'finally {' in read('ReAlertReceiver')
assert 'reminder_failed' in read('ReAlertReceiver')
assert 'source_removed_retained' in read('MissedAlertListener')
assert 'NOTIF_ID_A' in read('Notifier') and 'NOTIF_ID_B' in read('Notifier')
assert 'VISIBILITY_PRIVATE' in read('Notifier')
assert 'reminder_blocked_or_silent' in read('Notifier')
assert '.commit()' in read('AlertStore')
print('15 source contracts passed; NOT an Android runtime test.')

assert 'fun ackOne(' in read('Acker')
assert 'Acker.ackOne(this' in read('AckActivity')
assert '.putExtra("key", items.last().key)' in read('Notifier')
