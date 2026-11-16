#!/usr/bin/env bash
# Runs the projects in this folder, which are supposed to fail, and checks that each fails the
# way it should: with a clear message, and quickly. Pass extra Gradle arguments if needed, e.g.
# ./check.sh -Porg.gradle.java.installations.fromEnv=JAVA_HOME_25_X64
set -uo pipefail
cd "$(dirname "$0")"
root=../..
failed=0

expect_failure() { # project, server ("Spigot 1.8.8" / "Paper 26.3"), max seconds, expected texts...
    local project=$1 server=$2 max=$3; shift 3
    local type=${server%% *} version=${server#* }
    local versions
    if [ "$type" = "Paper" ]; then
        versions=(-PspigotTesting.versions= "-PspigotTesting.paperVersions=$version")
    else
        versions=("-PspigotTesting.versions=$version" -PspigotTesting.paperVersions=)
    fi
    local log; log=$(mktemp)
    local start=$SECONDS
    "$root/gradlew" --no-daemon --console=plain -p . ":$project:serverTest" -PspigotTesting.acceptEula=true \
        "${versions[@]}" ${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"} > "$log" 2>&1
    local status=$? took=$((SECONDS - start))
    if [ $status -eq 0 ]; then
        echo "FAIL $project on $server: the build passed, but it should fail"; failed=1; return
    fi
    for text in "$@"; do
        if ! grep -qF -- "${text//\{server\}/$server}" "$log"; then
            echo "FAIL $project on $server: output lacks \"${text//\{server\}/$server}\""; failed=1
            tail -40 "$log"; return
        fi
    done
    if [ $took -gt "$max" ]; then
        echo "FAIL $project on $server: took ${took}s, expected at most ${max}s"; failed=1; return
    fi
    echo "ok   $project on $server: failed as expected in ${took}s"
}

EXTRA_ARGS=("$@")
# Spigot doesn't load the harness when the plugin under test fails to load; modern Paper does.
for server in "Spigot 1.8.8" "Paper 26.3"; do
    expect_failure failing-test "$server" 240 \
        "1 server test(s) failed on {server}" \
        "failsOnPurpose()" \
        "this test fails on purpose" \
        "PASSED FixtureTest > passes()"
    expect_failure broken-plugin "$server" 240 \
        "{server} stopped without test results" \
        "fixture.DoesNotExist"
    expect_failure failing-enable "$server" 240 \
        "The plugin under test (FailingEnable)" \
        "failed while enabling" \
        "boom on enable"
done
exit $failed
