-- ## Model list 
--
-- #### **Function:** `watsonx_getmodels`
--
-- **Description:** Calls [watsonx.ai](https://www.ibm.com/products/watsonx-ai) to list available model ids for use with this API. 
-- 
-- <Aside type="note">No authentication is needed for this function. </Aside>

-- **Input parameters:**
-- - `TEXT` (required): The prompt text for the LLM.
-- - `MODEL_ID` (optional): The watsonx model ID to use (default: `meta-llama/llama-2-13b-chat`).
-- - `PARAMETERS` (optional): Extra parameters to the watsonx generation APIs.
-- 
-- **Return type:** 
-- - Result set with the following columns:
--    - `model_id`: The model ID.
--    - `label`: The model label.
--    - `provider` : The model provider.
--    - `short_description`: A short description of the model.
--
-- **Return value:**
-- - The list of available models

create or replace procedure aisqltkt.wx_getmodels()
  DYNAMIC RESULT SETS 1
  program type sub
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  declare apiResult clob(50000) ccsid 1208;

  DECLARE ResultSet CURSOR FOR
    select * from JSON_TABLE(QSYS2.HTTP_GET(
      aisqltkt.geturl('/foundation_model_specs')
    ), '$.resources[*]'
        COLUMNS(
          model_id VARCHAR(128) PATH '$.model_id',
          label VARCHAR(128) PATH '$.label',
          provider VARCHAR(128) PATH '$.provider',
          short_description VARCHAR(512) PATH '$.short_description'
        )
      ) x;

  OPEN ResultSet;
  SET RESULT SETS CURSOR ResultSet;
end;