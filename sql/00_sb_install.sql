-- ============================================================================
--  Sternbild · Oracle 21c · Service PDBORCL · Schema DEMO
--  00 – Komplettinstallation (als DEMO in SQL Developer mit F5 oder SQL*Plus)
--  GENERIERT aus gen/data.py – nicht von Hand aendern, sondern neu generieren.
-- ============================================================================

-- sqlplus DEMO@//localhost:1521/PDBORCL @00_sb_install.sql
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
@@99_sb_drop.sql
@@01_sb_schema.sql
@@02_sb_data.sql
WHENEVER SQLERROR CONTINUE
@@03_sb_check.sql
