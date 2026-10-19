# RingKV — Part 1: Project Overview and Learning Goals

> **Project type:** Educational distributed key-value store  
> **Language:** Java 21  
> **Networking:** TCP sockets  
> **Dependencies:** Java standard library; no external frameworks  
> **Context:** First exam material for Distributed Systems (lectures 1–8)

## 1. Why build RingKV?

RingKV is a small, intentionally imperfect distributed key-value store. Its purpose is **not** to become production-ready infrastructure. It is a hands-on way to learn why distributed systems need the abstractions and mechanisms introduced in class.

The guiding learning cycle is:

1. **Build a naive version** that solves the simplest case.
2. **Make a limitation visible** through a deliberate experiment.
3. **Explain what happened** using distributed-systems concepts.
4. **Improve the design** and identify what problems remain unresolved.

The goal is to associate each theoretical idea with concrete behavior observed in a system written from scratch. The code is a learning instrument; understanding the behavior is the main deliverable.

Java is used because it matches the language used in the course labs and final project, while garbage collection lets the project focus on networking, concurrency, and distributed protocols rather than manual memory management.

## 2. What is RingKV?

RingKV provides a tiny key-value interface:

```text
PUT alice 100
GET alice
INCR alice
DEL alice
```

The final educational system comprises multiple **independent Java processes**, each hosting a node with some subset of the keys. A client can contact an entry node, which determines which node is responsible for the requested key and routes the operation there.

Conceptually:

```text
                   Client
                     |
                     v
                 Entry node
                /    |    \
               v     v     v
            Node A  Node B  Node C
               \      |     /
                \-- overlay --/

      Each node is a separate JVM process.
      Nodes communicate using TCP sockets.
```

The logical overlay and the physical TCP connections are not the same thing: a routing relationship does not require a permanently open socket.

### Intended capabilities

- Accept `PUT`, `GET`, `INCR`, and `DEL` requests over TCP.
- Handle multiple clients concurrently.
- Place keys across multiple Java processes.
- Determine key ownership using consistent hashing on a ring.
- Route a request received by a non-owner node.
- Improve routing with simplified Chord finger tables.
- Distinguish a logical node name/identifier from its network address.
- Compare synchronous request-response behavior with queued asynchronous processing.
- Expose failure cases: delayed clients, lost responses, duplicate requests, unreachable nodes, and stale cached addresses.

**Non-goal:** Hide every failure. Failures are part of the curriculum; the project should make them observable and explainable.

## 3. Architecture and technical choices

### Network and execution model

- Each node runs in its own **JVM process** on a configurable port.
- Clients connect to nodes over **TCP** using Java's `Socket` and `ServerSocket`.
- Early iterations use a simple iterative server; later iterations handle concurrent clients with an `ExecutorService`.
- Data is held in memory; early prototypes can use a `HashMap`, with concurrency controls introduced when needed.
- The wire protocol is **newline-delimited UTF-8 text**, initially with one request and one response per connection.
- For simplicity, keys and values are text tokens without whitespace.
- No Spring Boot, REST framework, external database, external message broker, or orchestration layer is required.

Useful Java standard-library concepts include `ServerSocket`, `Socket`, `ExecutorService`, `ConcurrentHashMap`, `BlockingQueue`, and timeouts on socket operations.

### Illustrative protocol

A small request-response protocol makes application-layer framing, encoding, marshalling, and failure handling visible:

```text
# Requests
PUT  <requestId> <key> <value>
GET  <requestId> <key>
INCR <requestId> <key>
DEL  <requestId> <key>

# Responses
OK <value>
NOT_FOUND
ERROR <reason>
```

`requestId` is a client-generated UUID. As routing becomes more sophisticated, messages may also include a hop count and trace of visited nodes. An early prototype may start simpler before incorporating all fields.

The protocol is intentionally limited. For example, it does not provide arbitrary binary values, complex schemas, streaming payloads, or cryptographic transport security.

### Suggested code responsibilities

```text
ringkv/
├── src/
│   └── ringkv/
│       ├── ClientMain.java
│       ├── NodeMain.java
│       ├── NodeServer.java
│       ├── KeyValueStore.java
│       ├── Protocol.java
│       ├── ChordRouter.java
│       ├── DirectoryServer.java
│       └── NodeAddress.java
├── tests/
├── README.md
└── CONCEPTS.md
```

This is **not** a mandatory class layout. Responsibilities should be refactored as the problems become clearer.

## 4. The main learning phases

These phases represent a **conceptual progression**, not a daily coding schedule. Each introduces a problem whose resolution helps explain course material.

### Phase 1 — The naive server

**Main topics:** Introduction, architectures, processes, socket communication (lectures 1, 2, 3, 5, 6).

Begin with an iterative TCP server and an in-memory map. Observe what happens when a connected client delays or never finishes sending a command.

**Pain to experience:** One blocking connection can prevent an iterative server from servicing another client.

**Learn:** Blocking I/O, iterative server architecture, application protocols, and the motivation for concurrency.

### Phase 2 — Multiple clients and shared state

**Main topic:** Processes and threads (lecture 3).

Introduce a thread pool and shared state. Consider what happens when many clients attempt to increment the same value at once.

**Pain to experience:** Lost updates caused by an unsafe read-modify-write sequence.

**Learn:** Concurrency, shared memory, synchronization, atomic operations, and why a concurrent collection does not automatically make every multi-step operation atomic.

### Phase 3 — A miniature RPC protocol

**Main topic:** Communication and RPC (lecture 5).

Introduce explicit request IDs, request/response encoding, timeouts, and retries. Simulate a request that the server executes successfully but whose reply never arrives.

**Pain to experience:** The client cannot infer from a timeout whether the operation executed. A blind retry of `INCR` might modify state twice.

**Learn:** RPC stubs and marshalling, request-response semantics, idempotence, at-most-once behavior, and uncertainty under network failure. Duplicate detection needs recorded IDs; preserving this guarantee across crashes would require durable tracking and careful recovery design.

### Phase 4 — Data distribution across nodes

**Main topics:** Architectures, clusters, naming (lectures 1, 2, 4, 7).

Launch separate JVM nodes, partition the key space, and forward requests that arrive at a node other than the key owner.

**Pain to experience:** Finding a value is no longer equivalent to looking in the local map. A request may need another process, and that process may be unreachable.

**Learn:** Data placement, location transparency, remote communication, logical versus physical architecture, request forwarding, and the limitations of central coordination.

### Phase 5 — Simplified Chord routing

**Main topics:** Structured P2P and DHTs (lectures 2, 7).

Begin by routing around the ring through successor pointers. Then add Chord-style finger tables and compare lookup hop counts.

**Pain to experience:** Correct routing can still be inefficient when each lookup traverses many nodes.

**Learn:** Consistent hashing, circular identifier spaces, finger tables, forwarding rules, and why structured P2P lookups can be substantially more efficient than linear successor traversal.

### Phase 6 — Names, identifiers, and addresses

**Main topics:** Naming and resolution (lectures 7, 8).

Introduce a simple directory service mapping logical node names to TCP host/port addresses. Add a small cache with expiration.

**Pain to experience:** A node may have a valid logical identity but an out-of-date cached network address.

**Learn:** Names versus identifiers versus addresses, bootstrap discovery, location resolution, caching, TTLs, and stale entries. The directory is an analogy for name resolution, **not** a full DNS implementation.

### Phase 7 — Asynchronous message processing

**Main topic:** Message-oriented communication (lecture 6).

Introduce an operation such as `PUT_ASYNC`, queued in a bounded in-memory `BlockingQueue` for background processing.

**Pain to experience:** An acknowledgment that a job was *accepted* does not mean the write was *completed*. Queued work may disappear if a process crashes before applying it.

**Learn:** Synchronous versus asynchronous communication, transient versus persistent messaging, queueing, backpressure, and the boundaries of in-memory queues.

## 5. Simplified Chord model

To keep the algorithm small enough to inspect by hand, use a **5-bit identifier space**:

$\mathrm{ID} \in \{0, 1, \ldots, 31\}$

An illustrative fixed membership is:

| Node ID | Example TCP port |
| ---: | ---: |
| 2 | 9002 |
| 9 | 9009 |
| 17 | 9017 |
| 25 | 9025 |

Initially map a key `k` to:

```java
int keyId = Math.floorMod(k.hashCode(), 32);
```

The key belongs to the first active node **at or clockwise after** `keyId` on the identifier ring. Thus, with the example membership above, a key hashing to `12` belongs to node `17`.

For a node `p`, define the five finger-table entries by:

$FT_p[i] = \operatorname{successor}((p + 2^i) \bmod 32),\qquad i=0,\ldots,4.$

Key experiments:


- Determine a key's successor/owner by hand.
- Compute a node's finger table.
- Trace a lookup through successor-only routing.
- Trace the same lookup through finger-table routing.
- Count hops and explain the difference.
- Consider what becomes invalid when a node disappears.

The tiny key space is educational, not production grade: hash collisions and uneven distribution are expected. The initial ring uses **fixed membership**; automatic node join/leave and Chord stabilization are out of scope.

## 6. Experiments and acceptance criteria

The project should be evaluated by **observable behaviors** and the student's ability to explain them, not merely by whether commands work in an ideal case.

| Experiment | Observation or question to investigate | Concept |
| --- | --- | --- |
| One client connects but does not finish a request | Does an iterative server block other clients? | Blocking I/O, server concurrency |
| Many clients increment one key | Do unsafe updates get lost? Does the corrected implementation give the expected count? | Atomicity, synchronization |
| `INCR` executes but its response is dropped | Does a retry apply the increment twice? | RPC uncertainty, idempotence, duplicate handling |
| A client contacts a node that does not own the key | Can the request find the correct owner? | Distribution, routing, location transparency |
| Enable Chord finger tables | How do hop counts differ from successor-only routing? | DHTs, routing complexity |
| A node changes port while another retains its old address | How does cached location information affect communication? | Naming, caching, stale resolution |
| A queued asynchronous write is not processed before a crash | Can accepted but unprocessed work be lost? | Queues, delivery and durability semantics |

Experiments should involve explicit predictions, measurements/logs, and explanations. When appropriate, repeat them to distinguish reproducible behavior from timing-dependent observations.

## 7. Learning journal

Maintain `CONCEPTS.md` alongside the source code. After each meaningful experiment, write:

1. **Prediction:** What did I expect to happen?
2. **Observation:** What actually happened? Include logs, results, or hop counts.
3. **Explanation:** Which distributed-systems mechanism or limitation explains it?
4. **Change:** What did I modify to address the issue?
5. **Remaining limitations:** Which guarantees does the revised system still lack?

A useful self-test is: **Can I explain the implementation decision without looking at the code or slides?** If not, the concept probably needs another experiment or a conceptual review.

## 8. Relationship to the exam lectures

| Lecture | Themes | RingKV connection |
| --- | --- | --- |
| 1 — Introduction | Transparency, scaling, replication, reliability | Motivation for nodes, request forwarding, and visible failures |
| 2 — Architectures | Client-server, P2P, DHTs, middleware | Evolution from a single server to a structured overlay |
| 3 — Processes | Processes, threads, server organization | Separate JVMs, iterative/concurrent servers, shared state |
| 4 — Clusters and code migration | Load balancing, clusters, migration | Distributed requests and placement; **migration remains theory-only** |
| 5 — Communication and RPC | TCP/UDP, RPC, marshalling, failure semantics | Socket protocol, encoding, IDs, timeouts, retries |
| 6 — Message-oriented communication | Sockets, queues, MPI, gossip, streaming | TCP and asynchronous queue; **MPI/gossip/streaming remain mostly theory-only** |
| 7 — Naming | Identifiers, location, Chord, DHTs | Ring ownership, finger tables, logical names |
| 8 — Name resolution | DNS, hierarchical naming, caching, LDAP | Basic directory/cache; **full DNS and LDAP remain theory-only** |

**Important:** Building RingKV does not replace reviewing the lectures. Topics without direct implementations should be approached through short conceptual comparisons, diagrams, and exercises. Examples include migration of a running process, CDN/load-balancing designs, MPI, gossip, streaming, DNS hierarchy, and LDAP.

## 9. Deliberate scope boundaries

To keep the project focused on the first-exam syllabus, do **not** treat these as required features:

- Automatic membership changes, node-join/leave stabilization, or production Chord maintenance.
- Replication, failover, distributed consensus, Raft, or Paxos.
- Durable database storage, full ACID transactions, or crash-safe replay logs.
- A web UI, frontend framework, authentication system, or production-grade wire format.
- Docker/Kubernetes orchestration, cloud deployment, or advanced observability infrastructure.
- Full DNS, LDAP, MPI, media streaming, or gossip implementations.

These are worthwhile future subjects but would distract from the targeted learning objectives. The project may remain imperfect, provided its behavior and limitations are understood.

## 10. Definition of success

RingKV Part 1 succeeds when its author can:

- Explain the difference between local calls and remote requests, including the uncertainty introduced by network failures.
- Describe why concurrency improves responsiveness and where races can arise.
- Distinguish an acknowledged request from a completed or durable operation.
- Explain how a distributed system decides where data lives and how requests reach it.
- Calculate Chord key ownership and finger-table entries and interpret lookup paths.
- Distinguish node names, logical identifiers, and network addresses.
- Compare synchronous requests, transient queues, and durable messaging in terms of guarantees.
- Identify the guarantees **not** provided by this toy system, rather than mistaking a working demo for a reliable production service.

**The primary outcome is conceptual fluency grounded in implementation, not the number of features implemented.**

## 11. Further reading

- Andrew S. Tanenbaum and Maarten van Steen, *Distributed Systems*, 3rd edition: https://www.distributed-systems.net/index.php/books/ds3/
- Oracle, Java SE 21 networking APIs: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/package-summary.html
- Beej's Guide to Network Programming (C-oriented, useful for general socket concepts): https://beej.us/guide/bgnet/
- Original Chord paper (MIT): https://dspace.mit.edu/entities/publication/5ae198a3-2f6b-4e21-b90f-153b93b6ea67

---

*This document records the project vision, architectural constraints, learning phases, experiments, and educational outcomes. It intentionally does not prescribe an implementation task for a particular day or a dated coding schedule.*
