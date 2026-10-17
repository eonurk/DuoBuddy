// A held state request wakes on the next revision; ordinary GETs remain compatible.
export class StateDelivery {
  constructor({read, send, timeoutMs = 20000, maxClients = 8}) {
    this.read = read; this.send = send; this.timeoutMs = timeoutMs; this.maxClients = maxClients;
    this.clients = new Set();
  }
  wait(res, since, session) {
    const state = this.read();
    if (this.clients.size >= this.maxClients) {this.send(res, 503, {error: 'Too many state connections. Retry shortly.'}); return;}
    if (session !== state.session || since !== state.revision) {
      this.send(res, 200, state); return;
    }
    let timer;
    const remove = () => {clearTimeout(timer); this.clients.delete(finish); res.off('close', remove);};
    const finish = () => {remove(); if (!res.destroyed) this.send(res, 200, this.read());};
    this.clients.add(finish);
    res.once('close', remove);
    timer = setTimeout(finish, this.timeoutMs);
    timer.unref?.();
  }
  publish() {for (const finish of [...this.clients]) finish();}
}
