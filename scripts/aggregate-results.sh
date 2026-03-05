#!/bin/bash

RESULTS_DIR=${1:-.}

echo "=== Benchmark Results Aggregation ==="
echo "Source: $RESULTS_DIR"
echo ""

# Find all result directories
VARIANTS=$(find "$RESULTS_DIR" -maxdepth 1 -type d -name "*baseline*" -o -type d -name "*variant*" 2>/dev/null | sort)

if [ -z "$VARIANTS" ]; then
    echo "No benchmark results found in $RESULTS_DIR"
    exit 1
fi

echo "Found variants:"
for variant_dir in $VARIANTS; do
    echo "  - $(basename "$variant_dir")"
done
echo ""

# Display individual variant results
echo "=== Individual Variant Results ==="
for variant_dir in $VARIANTS; do
    VARIANT=$(basename "$variant_dir")
    echo ""
    echo "▶ $VARIANT"
    
    if [ -f "$variant_dir/RESULTS.txt" ]; then
        grep -E "Total Requests|Errors|Avg Response|Avg Memory|Samples Collected" \
            "$variant_dir/RESULTS.txt" | sed 's/^/  /'
    fi
done
echo ""

# Create comparison table
echo "=== Performance Comparison ==="
echo ""

# Header
printf "%-25s %-20s %-20s %-15s\n" "Variant" "Avg Response (ms)" "Throughput (req/s)" "Memory (MB)"
printf "%-25s %-20s %-20s %-15s\n" "---" "---" "---" "---"

for variant_dir in $VARIANTS; do
    VARIANT=$(basename "$variant_dir")
    
    AVG="N/A"
    THROUGHPUT="N/A"
    MEMORY="N/A"
    
    if [ -f "$variant_dir/jmeter-results.jtl" ] && command -v awk >/dev/null 2>&1; then
        TOTAL=$(tail -n +2 "$variant_dir/jmeter-results.jtl" 2>/dev/null | wc -l)
        if [ $TOTAL -gt 0 ]; then
            AVG=$(awk -F',' 'NR>1 {sum+=$2; count++} END {if(count>0) printf "%.0f", sum/count; else print "N/A"}' \
                "$variant_dir/jmeter-results.jtl" 2>/dev/null || echo "N/A")
            THROUGHPUT=$(awk -F',' 'NR>1 {count++} END {if(count>0) printf "%.1f", count/600; else print "N/A"}' \
                "$variant_dir/jmeter-results.jtl" 2>/dev/null || echo "N/A")
        fi
    fi
    
    if [ -f "$variant_dir/metrics.jsonl" ] && command -v jq >/dev/null 2>&1; then
        MEMORY=$(jq -s 'map(.jvm.memory.heap.used_bytes | select(. != null)) | if length > 0 then (add / length / 1048576 | round) else "N/A" end' \
            "$variant_dir/metrics.jsonl" 2>/dev/null || echo "N/A")
    fi
    
    printf "%-25s %-20s %-20s %-15s\n" "$VARIANT" "$AVG" "$THROUGHPUT" "$MEMORY"
done

echo ""
echo "=== Report Generated ==="
echo "Generated at: $(date)"
