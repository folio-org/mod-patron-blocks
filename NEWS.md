## 1.7.0 IN-PROGRESS

* Supports users interface version 15.1 16.0 (MODPATBLK-140)

## 1.6.0 2022-06-28
* Do not remove loan from summary when FEE_FINE_BALANCE_CHANGED event is received (MODPATBLK-124)
* Set `openLoan.itemLost` to false on LOAD_DUE_DATE_CHANGED event if due date changed by recall (MOTPATBLK-126)
* Delete user summary when synchronization generates no events (MODPATBLK-129)
* Update to RMB 34.0.0 (MODPATBLK-137)

## 1.5.0 2022-02-22
* Update to RMB 33.1.1, upsert with optimistic locking (MODPATBLK-102)
* Add raml description for period.json (MODPATBLK-103)
* New lines removal from logs (MODPATBLK-104)
* Upgrade to RMB 33.1.3 and Log4j 2.16.0 (MODPATBLK-106)
* Update copyright year (FOLIO-1021)
* Use new api-lint and api-doc (FOLIO-3231)
* Update RMB to 33.2.4 (MODPATBLK-113)
* Clear ITEM_AGED_TO_LOST events before synchronization (MODPATBLK-116)
* Stop automated patron blocks calculation when there are no blocks (MODPATBLK-117)
* Add maven-failsafe-plugin for ApiIT (MODPATBLK-121)

## 1.4.0 2021-09-30
* Increase `Maximum outstanding fee/fine balance` limit maximum value from 9999 to 999999 (MODPATBLK-92)
* Use optimistic locking for UserSummary records to avoid race condition and improve performance (MODPATBLK-91)
* Create LOAN_CLOSED event handler (MODPATBLK-97)
* Save `Grace period` at the moment of checkout (MODPATBLK-93)

## 1.3.0 2021-06-14
* Upgrade mod-pubsub-client to v2.3.0 (MODPATBLK-89)
* Upgrade RMB to v33.0.0, update Vert.x to v4.1.0 (MODPATBLK-86)

## 1.2.0 2021-03-10
* Add pubsub permissions to tenant API (MODPATBLK-85)
* Remove pubsub unregistering logic (MODPATBLK-84)
* Update RMB to v32.1.0, update Vert.x to v4.0.0 (MODPATBLK-75)
* Fix patron not blocked when maximum number of items charged out limit exceeded (MODPATBLK-83)
* Add support for ITEM_AGED_TO_LOST events (MODPATBLK-77)
* Allow 0 as valid entry for patron blocks limit value (MODPATBLK-79)
* Rename patron-blocks.events.post to pubsub.events.post
* Fix patron not blocked when outstanding fees/fines limit exceeded (MODPATBLK-69)
* Update pubsub client version (MODPATBLK-67)
* Synchronize job updates (MODPATBLK-66)
* Update RMB to v31.1.5, update Vert.x to v3.9.4 (MODPATBLK-63)
* Fix FEE_FINE_BALANCE_CHANGED event generation (MODPATBLK-65)
* Add mapping for FeeFineTypeId (MODPATBLK-62)
* Add a missing slash for pathPattern in ModuleDescriptor-template.json

## 1.1.0 2020-10-14
* Automated patron blocks data synchronization (MODPATBLK-41)
* Fix memory leak (MODPATBLK-43)
* Properly enforce fee/fine limit for automated patron blocks (MODPATBLK-48) 
* Upgrade to RMB 31.0.2 and JDK 11 (MODPATBLK-50)
* Correctly determine loan's overdue status (MODPATBLK-18, MODPATBLK-52)
* Fix 3 implemented automated patron blocks - block when limits are exceeded (MODPATBLK-34)
* Remove Maximum outstanding fee/fine balance automated patron block when condition removed by library (MODPATBLK-27)
* Add diagnostic GET endpoint for UserSummary objects (MODPATBLK-35)
* Add migration script to remove unnecessary fields from user_summary DB table (MODPATBLK-36)
* Delete open loan from user summary when last related lost item fee is closed (MODPATBLK-21)
* Allow additional properties in user schema (MODPATBLK-30)
* Create patron block when the limit is reached, not when it's exceeded (MODPATBLK-20)

## 1.0.0
* Register module as a publisher (MODPATBLK-17)
* Handle events published by mod-circulation and mod-feesfines (MODPATBLK-14, MODPATBLK-13, MODPATBLK-11, MODPATBLK-12, MODPATBLK-5, MODPATBLK-4)
* Add license statement (FOLIO-360)
* Update RMB version to 30.0.0 and Vertx to 3.9.0 (MODPATBLK-10)
* Implement Automated Patron Blocks API (MODPATBLK-7)
* Move patron block conditions and limits functionality to mod-patron-blocks (MODPATBLK-6)
* Create UserSummary entity, implement CRUD (MODPATBLK-3)
* Create initial mod-patron-blocks implementation (MODPATBLK-2) 
