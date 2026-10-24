"""Summarize saved bridge console logs. No hardware or third-party dependencies."""
import argparse
import collections
import json
import statistics
from pathlib import Path


def summarize(path):
    groups = collections.defaultdict(list)
    errors = collections.Counter()
    for line in path.read_text(encoding='utf-8-sig', errors='replace').splitlines():
        try:
            row = json.loads(line)
        except ValueError:
            if line.startswith(('Reader command failed:', 'Phone removed.', 'Phone declined.', 'No two-tap order')):
                errors[line] += 1
            continue
        if not isinstance(row, dict) or 'bytes' not in row or row.get('simulated') is not False:
            continue
        if row.get('acknowledged') is not True:
            errors['Transfer published but phone acknowledgement missing'] += 1
            continue
        seconds = row.get('seconds')
        if type(seconds) not in (float, int) or not 0 <= seconds < 3600:
            continue
        groups[(row['bytes'], row.get('chunks'), row.get('poll_ms', 'legacy'))].append(row)
    print('\n' + path.name)
    print('bytes  chunks  poll-ms  runs  mean-s  median-s  slowest-s  retries')
    for (size, chunks, profile), rows in sorted(groups.items(), key=lambda item: str(item[0])):
        times = [r['seconds'] for r in rows]
        print(f'{size:5}  {chunks:6}  {str(profile):>7}  {len(rows):4}  {statistics.mean(times):6.3f}  {statistics.median(times):8.3f}  {max(times):9.3f}  {sum(r.get("retries", 0) for r in rows):7}')
        measured = [r for r in rows if 'reader_exchange_seconds' in r]
        if measured:
            print('  Mean chunk totals: reader %.4fs; USB/host %.4fs' % (statistics.mean(r['reader_exchange_seconds'] for r in measured), statistics.mean(r['usb_host_seconds'] for r in measured)))
    for error, count in errors.items():
        print(f'  Error messages: {count} x {error}')
    if errors:
        print('  Messages are not unique attempts; do not interpret as a failure rate.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('logs', nargs='+', type=Path)
    for path in parser.parse_args().logs:
        summarize(path)
