# CLAUDE.md — inventory-stream

Last reviewed: 2026-01-09

Mirrors [`AGENTS.md`](./AGENTS.md) in this directory — no divergent policy here. See
that file for the module's role, the current handler/registry dispatch shape and the
active direction away from it (prefer a direct listener for new event types and topics;
leave existing registry-backed flows in place unless the change requires modifying
them; keep changes local to the selected path rather than restructuring the dispatcher),
environment configuration, and testing guidance.

Root-level guidance in [`../AGENTS.md`](../AGENTS.md) / [`../CLAUDE.md`](../CLAUDE.md)
still applies on top of this.
