# No Fuzz Notes

- Application specification is in [SPEC.md](./SPEC.md)
- Software architecture is in [ARCH.md](./ARCH.md)
- Always consult these documents when something is unclear
- Never implement more than a single increment
- Always complete an increment end-to-end unless something is unclear and requires resolution
- Dumb code is good. Write code obvious, don't try to be smart
- All functions/methods require at least a single-line comment explaining the purpose  
  - Never explain what, always explain why
- Assert invariants whereever possible
- Do not handle implementation bugs as errors
  - An implementation bug must never be swallowed, but should be surfaced.
  - Assertions are the right tool for this
 
