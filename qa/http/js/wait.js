export function wait(seconds) {
    const now = new Date().getTime()
    const waitUntil = now + seconds * 1000
    while (new Date().getTime() < waitUntil) {
        // tic or maybe tac?!
    }
}
