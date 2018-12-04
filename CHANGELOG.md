<a name="0.14.0"></a>
## 0.14.0 (2018-12-04)


#### Bug Fixes

* **broker-core:**
  *  update job state on update retries ([8c0a424b](https://github.com/zeebe-io/zeebe/commit/8c0a424b020651d653b5dedd2387b601b037ff5a))
  *  fix xor incident resolving ([ab592682](https://github.com/zeebe-io/zeebe/commit/ab5926822b3beaa404d1e8e008b80ed30e4462af))
  *  fix job state on cancel command ([024aafe3](https://github.com/zeebe-io/zeebe/commit/024aafe3152bac6605ef4526b3fa97e59fccee6c))
* **clients/java:**
  *  dont break cause chain ([be010d0e](https://github.com/zeebe-io/zeebe/commit/be010d0e0fcd898a91a5456cbe49d2912ccb34bd))
  *  fix default job poll interval and message ttl ([ab714eaa](https://github.com/zeebe-io/zeebe/commit/ab714eaaa7265d01c8da4bc861490fa512de6082))
* **exporters/elasticsearch:**  delayed flush should ignore bulk size ([beb15efc](https://github.com/zeebe-io/zeebe/commit/beb15efc2a7fa9c181cb9d7de017d93e27bccdff))

#### Features

* **bpmn-model:**
  *  enable non-interrupting time cycle boundary events ([bcf57f5e](https://github.com/zeebe-io/zeebe/commit/bcf57f5eba3dad93fe2ef8bc99926841f1d2624b))
  *  enable interrupting message boundary events ([ff1bddcd](https://github.com/zeebe-io/zeebe/commit/ff1bddcd44386f15a081139af197950b45d85588))
  *  enable interrupting timer boundary events ([edb345d1](https://github.com/zeebe-io/zeebe/commit/edb345d1138dc37d555f882ea99e02e1b5488f5e))
* **broker-core:**
  *  add support for event-based gateway ([425d6ca4](https://github.com/zeebe-io/zeebe/commit/425d6ca421cab2c09d1ba7cd8d41cb9ceb6ff16e))
  *  add support for interrupting message boundary events ([ea442ee9](https://github.com/zeebe-io/zeebe/commit/ea442ee9166dd8759b4880060efb5f176136279c))
  *  add job error message to failed jobs ([7baff1b7](https://github.com/zeebe-io/zeebe/commit/7baff1b79e97b3e60dd33c71100141535ddc66ba))
  *  impl new incident concept ([2a26ff58](https://github.com/zeebe-io/zeebe/commit/2a26ff58fc2b69a7d56d4862d5f59ec8ce87a4b6))
  *  add support for interrupting timer boundary events ([910b7b78](https://github.com/zeebe-io/zeebe/commit/910b7b78ba8167f2d1faec727e1f1cb21178f89c))
* **clients/go:**
  *  add resolve incident command ([e1a850db](https://github.com/zeebe-io/zeebe/commit/e1a850db1fe5023f1669a3555c2a0e7ff3cf6064))
  *  add error message to fail job command ([32bc691d](https://github.com/zeebe-io/zeebe/commit/32bc691db77a884ce5f93138059e84ef434c71bc))
* **clients/java:**  add resolve incident command ([25c1df38](https://github.com/zeebe-io/zeebe/commit/25c1df38a834032036cfbff77d38b3962062c13d))
* **clients/zbctl:**
  *  add resolve incident command ([3360289b](https://github.com/zeebe-io/zeebe/commit/3360289bb945c876e8a68d9684f0ac68934d376e))
  *  add fail job error message flag ([83269d8c](https://github.com/zeebe-io/zeebe/commit/83269d8cfbb50b09940860692d7f2c4085ad311e))
  *  implement publish message command ([1abca40a](https://github.com/zeebe-io/zeebe/commit/1abca40a7a96b9c7b92662bc35a74b4252f95771))
* **gateway:**  add incident resolve request to gateway ([e2eca8d2](https://github.com/zeebe-io/zeebe/commit/e2eca8d21c94ff82390eb2c9e24a2ad033db722d))



<a name="0.13.0"></a>
## 0.13.0 (2018-11-06)


#### Bug Fixes

* **broker-core:**
  *  exclusive split when default flow is first in XML ([75cd1539](https://github.com/zeebe-io/zeebe/commit/75cd1539c730440a87c2fefeaa96c253b5e856ac))
  *  add null check in job state controller ([10496ae7](https://github.com/zeebe-io/zeebe/commit/10496ae7b32f89e50c43896039fdc74a0c3f8b4d))
  *  correlate a message only once per wf instance ([892357cc](https://github.com/zeebe-io/zeebe/commit/892357cc35dd21d4d50c27ecf3edb5005f9d9809))
  *  fix concurrency problems with request metadata ([34b6b6fa](https://github.com/zeebe-io/zeebe/commit/34b6b6fa28e4552bfddf38f1c1e45457bb7a08ca))
* **exporters/elasticsearch:**  use correct index delimiter in root template ([e6c62be8](https://github.com/zeebe-io/zeebe/commit/e6c62be8df476cbd55ac5be0673633f4f35b3ec0))
* **gateway:**  use resource type provided in request instead of detecting ([9fbaccb5](https://github.com/zeebe-io/zeebe/commit/9fbaccb5965b211db6716ee8b2e1a81741708aa6)))

#### Breaking Changes

* **gateway:**  use resource type provided in request instead of detecting ([9fbaccb5](https://github.com/zeebe-io/zeebe/commit/9fbaccb5965b211db6716ee8b2e1a81741708aa6))

#### Features

* **broker-core:**
  *  add debug http exporter ([ef2d0203](https://github.com/zeebe-io/zeebe/commit/ef2d02035c09bdc4577551651aace7a20fb07450))
  *  handle intermediate timer catch event ([62111c35](https://github.com/zeebe-io/zeebe/commit/62111c3549357401bf20c1dec8620375837ebafe))
* **clients/go:**
  *  implement polling job worker ([09a21788](https://github.com/zeebe-io/zeebe/commit/09a21788b6c794c7ae1448230edfb0adc83d69ba))
  *  implement list workflows and get workflow ([5169ac27](https://github.com/zeebe-io/zeebe/commit/5169ac27c6448d083540dc7fe92c26a40b315711))
* **clients/zbctl:**
  *  add create worker command ([68d17600](https://github.com/zeebe-io/zeebe/commit/68d176008a9d22ad3e703bc43f1a241698ee47af))
  *  implement list workflows and get workflow ([429bdc47](https://github.com/zeebe-io/zeebe/commit/429bdc472de68d55ebdf43b7ad8bdc52d0ddd525))
  *  allow to configure the address to connect to ([0a3a4010](https://github.com/zeebe-io/zeebe/commit/0a3a401059b5f8128f9c746f9fa0b403f1ff6875))
* **dist:**  add standalone gateway script and configuration ([df212f12](https://github.com/zeebe-io/zeebe/commit/df212f12492d187d749382b67da346b29f4a694d))
* **gateway:**  add gateway configuration readable from toml file ([08b66441](https://github.com/zeebe-io/zeebe/commit/08b6644132e41b398d850408a35de78dbf654f98))
* **gateway-protocol:**  expose cluster settings in gateway protocol ([0035d39d](https://github.com/zeebe-io/zeebe/commit/0035d39d1bd2c3dce577551d19394ba484f58ade))



<a name="0.12.1"></a>
## 0.12.1 (2018-10-26)


#### Bug Fixes

* **broker-core:**
  *  exclusive split when default flow is first in XML ([3c91aa1d](https://github.com/zeebe-io/zeebe/commit/3c91aa1dc4f4740f50c3661cd87d8d565fae8ef3))
  *  fix concurrency problems with request metadata ([85e26e92](https://github.com/zeebe-io/zeebe/commit/85e26e926d131f23e8e5d9a28fac3a1e8da0f367))
* **exporters/elasticsearch:**  use correct index delimiter in root template ([9572500a](https://github.com/zeebe-io/zeebe/commit/9572500a0c889028e9966d0b31941092f3789caa))



<a name="0.12.0"></a>
## 0.12.0 (2018-10-16)


#### Bug Fixes

* **bpmn-model:**
  *  do not return raw type on connectTo ([9b45432a](https://github.com/zeebe-io/zeebe/commit/9b45432afab2dcef7d69588e1bbe31a27dc44e59))
  *  return typed builders from move to methods ([3cb70e08](https://github.com/zeebe-io/zeebe/commit/3cb70e089263fd31fd4a8a089de8b04cbd4115f5))
* **broker-core:**
  *  fix persistence cache ([eebc90f4](https://github.com/zeebe-io/zeebe/commit/eebc90f48128d24dd32a4adb45f4a7eb0bc47d8c))
  *  remove message subscription completely ([573ea845](https://github.com/zeebe-io/zeebe/commit/573ea845dd44ddfd846f832ad73388eaaf39dd94))
  *  fix unstable test ([7982444b](https://github.com/zeebe-io/zeebe/commit/7982444bdc572948c3a0dba5c27b7d0dca63ae7e))
  *  fix recovery ([cee0d1a4](https://github.com/zeebe-io/zeebe/commit/cee0d1a44036c30149ca90558f475f4783f7cddb))
  *  fix offset assert in messages ([efa4d563](https://github.com/zeebe-io/zeebe/commit/efa4d5630767d8f6f7080f23d0c7f92dfcd7650e))
  *  fix reprcoessing of deployments ([8cfff928](https://github.com/zeebe-io/zeebe/commit/8cfff92855d9e17f5b581dfd435a8acf9eacba4d))
  *  set flow scope payload after output mapping ([30ef6d69](https://github.com/zeebe-io/zeebe/commit/30ef6d69d1c52e19e37701bc418d73cfdd25a2d2))
  *  fix state byte ordering ([c7b59c4b](https://github.com/zeebe-io/zeebe/commit/c7b59c4b6a8426ae2e95fc0b923c4b01261d9ecf))
  *  move standalone broker into dist ([a26bf9a3](https://github.com/zeebe-io/zeebe/commit/a26bf9a39c9ce3771e08efae9c8c31c19b546411))
  *  messages with same name and correlation key ([366022a7](https://github.com/zeebe-io/zeebe/commit/366022a74ae52ae85d84b2bf18bb8774e88a221b))
  *  find message subscription ([38bb023e](https://github.com/zeebe-io/zeebe/commit/38bb023eefba7cb5fe26600612c3367117af7bef))
  *  reliably activate jobs ([8947ebfb](https://github.com/zeebe-io/zeebe/commit/8947ebfba76244866338408fadef092006e48621))
  *  message stream processors work with multiple partitions ([585c3615](https://github.com/zeebe-io/zeebe/commit/585c36156b8bd1283be369ee271410abd72299c7))
  *  snapshot replication on leader change ([64a0ff72](https://github.com/zeebe-io/zeebe/commit/64a0ff72dcc2b641ca93ca0bb86b7e8ba296721c))
* **clients/go:**  add missing retries parameter to fail job command ([f772eb55](https://github.com/zeebe-io/zeebe/commit/f772eb55cae05537dc344b62dd302960d616bcca))
* **dispatcher:**  decrease total work ([8136cdef](https://github.com/zeebe-io/zeebe/commit/8136cdef28e4e9cc5ec08f8de2678153a7114211))
* **dist:**  remove semicolon from cfg ([e617f378](https://github.com/zeebe-io/zeebe/commit/e617f378f0585759f03f2e7d002d395883e16ac2))
* **exporter:**  add aliases to ES indices ([7e9fffef](https://github.com/zeebe-io/zeebe/commit/7e9fffef7160c9aecbddaf2495baca6386affef5))
* **gateway:**  use NonBlockingMemoryPool to avoid long timeouts ([17e2f0aa](https://github.com/zeebe-io/zeebe/commit/17e2f0aac92f8cc0f0c6133e3162d84824edb6c5))
* **job:**  rewirte job append ([3167d302](https://github.com/zeebe-io/zeebe/commit/3167d302ae107bae39d6926d30ef396fa582eebf))
* **logstreams:**  do not close twice ([9a075c74](https://github.com/zeebe-io/zeebe/commit/9a075c74090a7fbad8c6629074ca4c80f8c9fa2f))
* **raft:**  persist raft members list on every member change ([dfa958a8](https://github.com/zeebe-io/zeebe/commit/dfa958a849160f1a3b43eb0ba22938331e4de845))
* **scheduler:**  handle race condition where job is added to dropped queue ([3304862f](https://github.com/zeebe-io/zeebe/commit/3304862f6dc69ce797b9053e300e3535724fee5a))
* **transport:**  retry send message if channel is not open ([ebb718d3](https://github.com/zeebe-io/zeebe/commit/ebb718d3e6323eebbdd93de73d29f2273e8505b9))

#### Features

* **bpmn-model:**
  *  extensions for payload mapping ([a4732a2f](https://github.com/zeebe-io/zeebe/commit/a4732a2fd3f5e2c5b8173951ca8f1158b99aca36))
  *  validate supported elements and event definitions ([950038cf](https://github.com/zeebe-io/zeebe/commit/950038cfc4eccb373e9ed4c4937d6cb764f4baa0))
  *  support intermediate catch event and receive task ([6741b6f7](https://github.com/zeebe-io/zeebe/commit/6741b6f7fdac6173ebbefdc8c74c4804ba152d4c))
* **broker-core:**
  *  implicit parallel split ([4eff3a66](https://github.com/zeebe-io/zeebe/commit/4eff3a660a46f83a1543cd3d5e452a2496052623))
  *  complete job by key ([5835cf2f](https://github.com/zeebe-io/zeebe/commit/5835cf2f69c6039d8eb4b36d15f8db35a39e6d95))
  *  fail jobs by key and retries ([1c08141e](https://github.com/zeebe-io/zeebe/commit/1c08141e4548b118c93ec34df50735fa1e34d115))
  *  update job retries by key ([d15bfd50](https://github.com/zeebe-io/zeebe/commit/d15bfd50f17a3c315e638caa4861bb6be603cf6b))
  *  payload update with key and payload only ([afa99c45](https://github.com/zeebe-io/zeebe/commit/afa99c45d964762de9348ff6b3828b2135ead938))
  *  workflow instance cancellation by key ([c4c78479](https://github.com/zeebe-io/zeebe/commit/c4c78479c9b39bd8864a86b8a0d14a493d0ea8fb))
  *  merge token payloads on scope completion ([9d42e78a](https://github.com/zeebe-io/zeebe/commit/9d42e78ad636640c7c08941ea58ffad00a6e34c4))
  *  merge payloads on parallel gateway merge ([2c7370e3](https://github.com/zeebe-io/zeebe/commit/2c7370e3a00059878955f768e0caedea9fc04d4c))
  *  introduce message state controller ([92ed1264](https://github.com/zeebe-io/zeebe/commit/92ed126478d03b7906f713dee0119712c5480813))
  *  BPMN merging parallel gateway ([83743595](https://github.com/zeebe-io/zeebe/commit/83743595ddec7fdfd7161ddc00e3792456ccdfc6))
  *  install partitions via cfg ([c961adf7](https://github.com/zeebe-io/zeebe/commit/c961adf7b91d851c4d64417646101baa190fc99a))
  *  create partitions matrix ([497a2898](https://github.com/zeebe-io/zeebe/commit/497a2898eadf3502d4ef09be04d55f8912faa36e))
  *  forking parallel gateway ([d40aa3ec](https://github.com/zeebe-io/zeebe/commit/d40aa3eca31099ab7207bc875fe934efeb49847c))
  *  add debug exporter ([5e8ca251](https://github.com/zeebe-io/zeebe/commit/5e8ca2516a71a6a5976880288b37e22dc30af3e3))
  *  create partition ids in cluster cfg ([039a5798](https://github.com/zeebe-io/zeebe/commit/039a57981c202bbcda705b4870d401a26ef150a0))
  *  allow to set data directories as environment variable ([3eedf6ea](https://github.com/zeebe-io/zeebe/commit/3eedf6ead00790844f1d5106418c3406f58dee8a))
  *  allow to set initial contact points as environment variable ([ab7cfda6](https://github.com/zeebe-io/zeebe/commit/ab7cfda620e962a433c0bdc51eba8bb907b5932a))
  *  allow to set host as environment variable ([b8f5131e](https://github.com/zeebe-io/zeebe/commit/b8f5131ece978edca79010dd31d33f9ad6e754ba))
  *  add exporter manager service ([f6f71d0b](https://github.com/zeebe-io/zeebe/commit/f6f71d0b066b8df729ffeb951f5b2790f262dfc1))
  *  add node id to configuration ([137c5621](https://github.com/zeebe-io/zeebe/commit/137c5621df9f0708511d0fb0ca168b1a17244259))
  *  correlate message resilient ([0f850910](https://github.com/zeebe-io/zeebe/commit/0f850910070424b0cccb989ed8d6c400f6e5fc9a))
  *  open a message subscription when a receive task is entered ([eec02854](https://github.com/zeebe-io/zeebe/commit/eec02854febc592868a96affff5e96b15694762b))
  *  open message subscription resilient ([816e2c0c](https://github.com/zeebe-io/zeebe/commit/816e2c0c90ecfcc6214153b744dda2404e200229))
  *  publish message with TTL ([de7d7604](https://github.com/zeebe-io/zeebe/commit/de7d7604d7f92dfd7ad0e0999b1322dd3e705977))
  *  embedded subprocess ([e54c7070](https://github.com/zeebe-io/zeebe/commit/e54c7070790a8999d05ff62e1984cb487e603fca))
  *  correlate a message to all subscriptions ([60fd1ae1](https://github.com/zeebe-io/zeebe/commit/60fd1ae1a8df080d3cc4c53269aae4b86547035e))
  *  open message subscription when catch event is entered ([9223947d](https://github.com/zeebe-io/zeebe/commit/9223947dbda91c97ac5a49da889e51133b073bdc))
  *  a message can be published idempotent ([dafc5294](https://github.com/zeebe-io/zeebe/commit/dafc529448e0681412383805d4381915b4456a41))
  *  a message can be published ([ac43219d](https://github.com/zeebe-io/zeebe/commit/ac43219db4d1c9542d0b88b06172d8ae3efeb62a))
  *  add port offset network configuration parameter ([3e5755d9](https://github.com/zeebe-io/zeebe/commit/3e5755d9f1fb1186ed5fe17d4783493783814e55))
  *  handle fetch-created-topics request ([1a20e311](https://github.com/zeebe-io/zeebe/commit/1a20e31178cc8e7abbb7b8ad8f264f5394797205))
  *  implement batch job activation ([5f3920ab](https://github.com/zeebe-io/zeebe/commit/5f3920ab669fa85d8324345a5e32dabb73c64c58))
  *  make keys global unique ([47053f38](https://github.com/zeebe-io/zeebe/commit/47053f382b18e8420a4fa68f725254668911f424))
  *  migrate instance index to RocksDb ([23a5d036](https://github.com/zeebe-io/zeebe/commit/23a5d036bf100de68477ddf5313a7a658569cdfa))
  *  migrate workflow cache ([0ed3275d](https://github.com/zeebe-io/zeebe/commit/0ed3275dee1e8e585f88e004dc054054760867c3))
  *  migrate workflow repository ([5433844e](https://github.com/zeebe-io/zeebe/commit/5433844e3347260dc0af5aa2908f4a135f42d8f4))
  *  configure cluster via cfg ([3d5e6271](https://github.com/zeebe-io/zeebe/commit/3d5e6271aae1ff7f80b862fe5b348cca4f1bd662))
  *  push deployment to remaining partitions ([38c699e5](https://github.com/zeebe-io/zeebe/commit/38c699e5bcf17c767e9c941273e1bf35d1ddb00c))
  *  change zeebe ports ([c0bd61c0](https://github.com/zeebe-io/zeebe/commit/c0bd61c095ad6e2fa0ad5529733f49d97dc9a252))
  *  migrates JobInstanceStreamProcessor to use RocksDB for state ([87182d3c](https://github.com/zeebe-io/zeebe/commit/87182d3cdc841a62175afd17a482f38d5169a8cd))
  *  integrates RocksDB as state backend for stream processors ([53ff0b5d](https://github.com/zeebe-io/zeebe/commit/53ff0b5d3d57e8dcc38ffd7c45c6d1f76a785da1))
* **clients/go:**
  *  implement activate jobs command ([2e16bea6](https://github.com/zeebe-io/zeebe/commit/2e16bea66387fcbb1926a9771683a25092e8da68))
  *  implement update job retries ([b3b763b3](https://github.com/zeebe-io/zeebe/commit/b3b763b33e858c1994697013fc401725770d34dc))
  *  implement fail job ([dcc1860f](https://github.com/zeebe-io/zeebe/commit/dcc1860fc50ff59b7d40b8d4aef4bc5523fe344b))
  *  implement complete job ([adb698e3](https://github.com/zeebe-io/zeebe/commit/adb698e375c587be1f7f49bc8f1dd6bee390678d))
  *  implement update payload ([258e1eac](https://github.com/zeebe-io/zeebe/commit/258e1eac1d8855bab7a61b7e6ebbfd7f26bc2ad4))
  *  Added simple golang client with health check ([74373755](https://github.com/zeebe-io/zeebe/commit/74373755974c20431204548f5d34ec5b82637dcc))
  *  Added automatic generation of Golang proto code ([5ff13ae2](https://github.com/zeebe-io/zeebe/commit/5ff13ae2e39aa9379ede2357748330e1f76c48fd))
  *  Added cancel workflow instance ([4838572d](https://github.com/zeebe-io/zeebe/commit/4838572df42cdadd69943d6154b2e5ec607a3bab))
  *  Added create job rpc on golang client ([725ce609](https://github.com/zeebe-io/zeebe/commit/725ce6097f6a0fcf912b2166b3712d5b6be2a4ab))
  *  Added golang client and fixed tests ([3bbd7cfc](https://github.com/zeebe-io/zeebe/commit/3bbd7cfc20a615624e7596b0552f65be13e456ea))
  *  Added publish message rpc on golang client ([5169ddf0](https://github.com/zeebe-io/zeebe/commit/5169ddf07244f333da8c1b3b7d4dca1ea0a9813a))
* **clients/java:**
  *  publish message with java client ([aafd8ba3](https://github.com/zeebe-io/zeebe/commit/aafd8ba33ce52a53d5df95dbe652991d4030bdcf))
  *  Java client switched to use client stub ([8e4a0bcc](https://github.com/zeebe-io/zeebe/commit/8e4a0bcce379f749ea68886d5c4f4fe470f5f989))
  *  implement polling job workers ([1134fc7d](https://github.com/zeebe-io/zeebe/commit/1134fc7dcc8c116c6fbfe3b93ebe095d36e8c531))
  *  implement get workflow and list workflows ([764398a1](https://github.com/zeebe-io/zeebe/commit/764398a142c7fa01f11dc4fef731e81b13ac6c32))
  *  complete job by key ([8030a88c](https://github.com/zeebe-io/zeebe/commit/8030a88c9adc42946d04e42cf8e041c91c742ffd))
  *  fail job with key and retries ([7f66d441](https://github.com/zeebe-io/zeebe/commit/7f66d44153f957effceeb19f2ecf2e9ed40678a0))
  *  update job retries by key ([434b6364](https://github.com/zeebe-io/zeebe/commit/434b63647fd606b4f2df674744e24cbd19d85234))
  *  update payload request ([468002a2](https://github.com/zeebe-io/zeebe/commit/468002a23d2321f08627ed1abbd49f21f99ac063))
  *  cancel workflow instance by key ([314ef859](https://github.com/zeebe-io/zeebe/commit/314ef859134a4dd9e3f6ea653bdd2f965a272814))
  *  create workflow instance request ([edcfbb02](https://github.com/zeebe-io/zeebe/commit/edcfbb02743566cd4b7ede7643082237db247f0f))
  *  add create job request ([c45fc3d1](https://github.com/zeebe-io/zeebe/commit/c45fc3d1b23f2e427cdb22786c670f14cd2e45a0))
* **clients/zbctl:**
  *  implement key based commands ([859f13f7](https://github.com/zeebe-io/zeebe/commit/859f13f70f08b856bef65a0e41f4fcd120aa89c0))
  *  add command to generate shell completion ([4d0163d9](https://github.com/zeebe-io/zeebe/commit/4d0163d928fd1b47e61ec5ab206dfe5a6df99171))
  *  Added zbctl with create instance, job, deploy and status commands ([fe9672a6](https://github.com/zeebe-io/zeebe/commit/fe9672a608e7872f028db55502934cef9d61f422))
* **dist:**  add new gossip config prop ([a5dc8447](https://github.com/zeebe-io/zeebe/commit/a5dc844797fa7ad93f69013472d9004ceed06eb9))
* **documentation:**
  *  parallel gateway ([121446b9](https://github.com/zeebe-io/zeebe/commit/121446b9919753f6dd93e26d9babbf22a55f7e47))
  *  overview of BPMN coverage ([2d11dcf6](https://github.com/zeebe-io/zeebe/commit/2d11dcf626b403de4bfd1b4c354d0952bad3fdf5))
  *  BPMN sub process ([ce73e00a](https://github.com/zeebe-io/zeebe/commit/ce73e00a08762a99fbdda3f586745af85b5e720b))
* **exporters:**
  *  add elasticsearch exporter ([12a70dbd](https://github.com/zeebe-io/zeebe/commit/12a70dbd464214fb9e36d222989305362874ba8c))
  *  adds stream processor to export records ([3752034a](https://github.com/zeebe-io/zeebe/commit/3752034af9368e66326d2d7368a79534ffab9b6a))
  *  load, configure, and validate exporters ([07bff107](https://github.com/zeebe-io/zeebe/commit/07bff10739a2d6980777519e6e67b04a70cbcac1))
  *  introduce new zb-exporter module ([0aab5e77](https://github.com/zeebe-io/zeebe/commit/0aab5e77813fb82757bc177f5eff35c792698387))
  *  adds exporter documentation ([9a8a9a0b](https://github.com/zeebe-io/zeebe/commit/9a8a9a0b0bc1e0a29fbf34e8d8d84a32a9d981ae))
  *  add AssertJ asserts for exporter records ([c16d3341](https://github.com/zeebe-io/zeebe/commit/c16d33418ea70c8ef981559be9a2136ce24bffff))
* **gateway:**
  *  implement activate jobs rpc call ([06f4e463](https://github.com/zeebe-io/zeebe/commit/06f4e463efd3ca1be67056660f12abd4ad7867d8))
  *  implement list workflows and get worklow ([0e84e7e4](https://github.com/zeebe-io/zeebe/commit/0e84e7e4a5386c8707120dc0b2227bcb82b769d2))
  *  cancel workflow instance ([e1d0b6ca](https://github.com/zeebe-io/zeebe/commit/e1d0b6ca779e5737c3e4145b4f12725536008da7))
  *  deploy workflow on java and go ([e5afa87a](https://github.com/zeebe-io/zeebe/commit/e5afa87a7fdb15febc470f9bc51d0399daa2de62))
  *  Added all needed infrastructure for gateway ([0ab54eba](https://github.com/zeebe-io/zeebe/commit/0ab54eba82d6e9987ecce11d1cea5c825437da23))
  *  expose scope instance key ([a8954fa4](https://github.com/zeebe-io/zeebe/commit/a8954fa439cb062acc65411296d94d588e510163))
  *  publish a message via Java Client ([96f4f84e](https://github.com/zeebe-io/zeebe/commit/96f4f84ee3c0a49e4350888ae359645566b4485f))
* **gateway-protocol:**  add ListWorkflows and GetWorkflow methods ([ffdc1098](https://github.com/zeebe-io/zeebe/commit/ffdc109897af4b4e42dfe09ad77812e5a09d1fac))
* **gossip:**  send sync request repeatedly ([ae9373ee](https://github.com/zeebe-io/zeebe/commit/ae9373ee85e9d7c4824ca5560bb8bbeb77602a2d))
* **json-path:**
  *  non-strict mapping extraction ([a116e60f](https://github.com/zeebe-io/zeebe/commit/a116e60fce82a18da75fd2c2d6667641ee878d5b))
  *  new mapping type to collect a result in an array ([eadd29c1](https://github.com/zeebe-io/zeebe/commit/eadd29c125f83661993c88351a1bc6cfa1604681))
* **logstreams:**  expose rocksb internal api ([07df1302](https://github.com/zeebe-io/zeebe/commit/07df130275641a7b6cdb1035878671fbcc312bf0))
* **msg-pack:**  merge multiple documents ([504ce125](https://github.com/zeebe-io/zeebe/commit/504ce125dc921c0f30ada33aa66b1208689dd93e))
* **transport:**  add endpoint registry ([b24b6ff5](https://github.com/zeebe-io/zeebe/commit/b24b6ff5c5d42a8801c008fbb4e52982c4fe30ac))
