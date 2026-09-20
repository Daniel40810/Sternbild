-- ============================================================================
--  Sternbild · Oracle 21c · Service PDBORCL · Schema DEMO
--  99 – Alles entfernen (Neuinstallation)
--  GENERIERT aus gen/data.py – nicht von Hand aendern, sondern neu generieren.
-- ============================================================================

BEGIN
  FOR r IN (SELECT 'DROP TABLE ' || t || ' CASCADE CONSTRAINTS PURGE' AS stmt
              FROM (SELECT 'SB_METEOR_SHOWER' t FROM DUAL UNION ALL SELECT 'SB_FIGURE_STROKE' FROM DUAL
                    UNION ALL SELECT 'SB_ZODIAC_PERIOD' FROM DUAL UNION ALL SELECT 'SB_SCENE_ITEM' FROM DUAL
                    UNION ALL SELECT 'SB_SCENE' FROM DUAL UNION ALL SELECT 'SB_CONST_LINE' FROM DUAL
                    UNION ALL SELECT 'SB_STAR' FROM DUAL UNION ALL SELECT 'SB_CONSTELLATION' FROM DUAL)
             WHERE t IN (SELECT TABLE_NAME FROM USER_TABLES)
            UNION ALL
            SELECT 'DROP SEQUENCE ' || SEQUENCE_NAME FROM USER_SEQUENCES
             WHERE SEQUENCE_NAME IN ('SB_CONST_SEQ','SB_STAR_SEQ','SB_LINE_SEQ','SB_SCENE_SEQ','SB_PERIOD_SEQ','SB_STROKE_SEQ','SB_SHOWER_SEQ'))
  LOOP
    EXECUTE IMMEDIATE r.stmt;
  END LOOP;
END;
/
