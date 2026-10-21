#!/usr/bin/env bash
# Interactive Gradle menu. Run from Git Bash: ./build-menu.sh
# Run an option directly by its shortcut: ./build-menu.sh rebuild

set -u

cd "$(dirname "$0")" || exit 1

export JAVA_HOME="/c/Users/RogerPhilippePereira/.jdks/corretto-17.0.20.1"

if [ ! -d "$JAVA_HOME" ]; then
    echo "JDK not found: $JAVA_HOME"
    exit 1
fi

# key|label|tasks|info
options=(
    "dev|Assemble dev debug|assembleDevelopmentDebug|APK debug do flavor development (incremental)."
    "rebuild|Clean + assemble dev debug|clean assembleDevelopmentDebug|Build completo do zero. Use quando o APK sai inconsistente (NoClassDefFoundError)."
    "install|Install dev on device|installDevelopmentDebug|Gera se preciso e instala no aparelho conectado (adb)."
    "prod|Assemble prod debug|assembleProductionDebug|APK debug do flavor production."
    "prodgp|Assemble prodGP debug|assembleProductionGPDebug|APK debug do flavor productionGP."
    "debug|Assemble all debug|assembleDebug|APKs debug dos três flavors."
    "dev-release|Assemble dev release|assembleDevelopmentRelease|APK release do development, ofuscado e sem assinatura."
    "prod-release|Assemble prod release|assembleProductionRelease|APK release do production, ofuscado e sem assinatura."
    "prodgp-release|Assemble prodGP release|assembleProductionGPRelease|APK release do productionGP, ofuscado e sem assinatura."
    "release|Assemble all release|assembleRelease|APKs release dos três flavors, sem assinatura."
    "clean|Clean|clean|Apaga app/build. O próximo build recompila tudo."
    "build|Build|build|Compila todas as variantes e roda testes e lint. Demorado."
    "stop|Stop daemons|--stop|Para todos os daemons. Interrompe build em andamento no Android Studio."
)

keys=()
labels=()
tasks=()
infos=()
for entry in "${options[@]}"; do
    IFS='|' read -r k l t i <<< "$entry"
    keys+=("$k")
    labels+=("$l")
    tasks+=("$t")
    infos+=("$i")
done

DIM=$'\e[2m'
REV=$'\e[7m'
CYAN=$'\e[36m'
GREEN=$'\e[32m'
RED=$'\e[31m'
RESET=$'\e[0m'

clear_screen() {
    printf '\e[H\e[2J'
}

print_menu() {
    local selected=$1
    clear_screen
    echo "${CYAN}=== Gradle · $(basename "$JAVA_HOME") ===${RESET}"
    echo
    for i in "${!keys[@]}"; do
        if [ "$i" -eq "$selected" ]; then
            printf ' %s> %-28s %-15s%s\n' "$REV" "${labels[$i]}" "${keys[$i]}" "$RESET"
        else
            printf '   %-28s %s%s%s\n' "${labels[$i]}" "$DIM" "${keys[$i]}" "$RESET"
        fi
    done
    echo
    echo " ${DIM}────────────────────────────────────────────────────────────${RESET}"
    echo " ${CYAN}gradlew ${tasks[$selected]}${RESET}"
    echo " ${infos[$selected]}"
    echo
    echo " ${DIM}↑↓ move · Enter run · Esc exit${RESET}"
}

run_option() {
    local index=$1
    echo "${CYAN}> ./gradlew ${tasks[$index]}${RESET}"
    local start=$SECONDS
    # shellcheck disable=SC2086
    ./gradlew ${tasks[$index]}
    local status=$?
    echo
    if [ $status -eq 0 ]; then
        echo "${GREEN}OK in $((SECONDS - start))s${RESET}"
    else
        echo "${RED}FAILED (exit $status) in $((SECONDS - start))s${RESET}"
    fi
    return $status
}

if [ $# -gt 0 ]; then
    for i in "${!keys[@]}"; do
        if [ "${keys[$i]}" = "$1" ]; then
            run_option "$i"
            exit $?
        fi
    done
    echo "Unknown shortcut: $1"
    echo "Available: ${keys[*]}"
    exit 1
fi

selected=0
count=${#keys[@]}
while true; do
    print_menu "$selected"
    key=''
    IFS= read -rsn1 key || exit 0
    case "$key" in
        $'\e')
            rest=''
            read -rsn2 -t 0.1 rest
            case "$rest" in
                '[A') selected=$(((selected - 1 + count) % count)) ;;
                '[B') selected=$(((selected + 1) % count)) ;;
                '')
                    clear_screen
                    exit 0
                    ;;
            esac
            ;;
        '')
            clear_screen
            run_option "$selected"
            echo
            read -rsn1 -p "${DIM}Press any key to return to the menu...${RESET}"
            ;;
    esac
done
