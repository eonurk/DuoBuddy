package family.tv.companion;

import org.json.*;
import java.util.UUID;

/** Provider wire formats; no Android dependency so signed tool round trips can be tested. */
final class AiProtocol {
    static final String OPENAI="openai",GEMINI="gemini",LUNA="gpt-6-luna",FLASH="gemini-3.1-flash-lite";
    static String label(String provider){return GEMINI.equals(provider)?"Google Gemini":"OpenAI";}
    static String defaultModel(String provider){return GEMINI.equals(provider)?FLASH:LUNA;}
    static void validateSelection(String provider,String model){
        if(!OPENAI.equals(provider)&&!GEMINI.equals(provider))throw new IllegalArgumentException("Choose OpenAI or Google Gemini.");
        if(model==null||!model.matches("[A-Za-z0-9._-]{1,100}"))throw new IllegalArgumentException("Invalid model ID.");
    }
    static String endpoint(String provider){return GEMINI.equals(provider)?"https://generativelanguage.googleapis.com/v1beta/openai/chat/completions":"https://api.openai.com/v1/responses";}
    static JSONObject request(String provider,String model,String instructions,JSONArray input,JSONArray tools){
        validateSelection(provider,model);
        if(OPENAI.equals(provider)){
            JSONObject r=TvJson.object("model",model,"instructions",instructions,"input",input,"tools",tools,"parallel_tool_calls",false,"max_output_tokens",2000,"store",false,"prompt_cache_key","duobuddy-tv");
            if(LUNA.equals(model))try{r.put("reasoning",TvJson.object("effort","none"));r.put("include",TvJson.array("reasoning.encrypted_content"));}catch(JSONException e){throw new IllegalArgumentException(e);}
            return r;
        }
        JSONArray messages=TvJson.array(TvJson.object("role","system","content",instructions));
        for(int i=0;i<input.length();i++){
            JSONObject item=input.optJSONObject(i);if(item==null)continue;
            if("provider_message".equals(item.optString("type"))){messages.put(item.optJSONObject("message"));continue;}
            if(item.optBoolean("providerProjection"))continue;
            if("function_call_output".equals(item.optString("type"))){messages.put(TvJson.object("role","tool","tool_call_id",item.optString("call_id"),"content",item.optString("output")));continue;}
            if(!item.has("role"))continue;
            Object content=item.opt("content");
            if(content instanceof JSONArray){
                JSONArray parts=new JSONArray();JSONArray originals=(JSONArray)content;
                for(int j=0;j<originals.length();j++){
                    JSONObject part=originals.optJSONObject(j);if(part==null)continue;
                    parts.put("input_image".equals(part.optString("type"))?TvJson.object("type","image_url","image_url",TvJson.object("url",part.optString("image_url"))):TvJson.object("type","text","text",part.optString("text")));
                }
                content=parts;
            }
            messages.put(TvJson.object("role",item.optString("role"),"content",content));
        }
        JSONArray functions=new JSONArray();
        for(int i=0;i<tools.length();i++){
            JSONObject tool=tools.optJSONObject(i);if(tool==null||!"function".equals(tool.optString("type")))continue;
            functions.put(TvJson.object("type","function","function",TvJson.object("name",tool.optString("name"),"description",tool.optString("description"),"parameters",tool.optJSONObject("parameters"))));
        }
        JSONObject r=TvJson.object("model",model,"messages",messages,"max_tokens",2000);
        try{if(functions.length()>0){r.put("tools",functions);r.put("tool_choice","auto");}if(FLASH.equals(model))r.put("reasoning_effort","minimal");}catch(JSONException e){throw new IllegalArgumentException(e);}
        return r;
    }
    static JSONObject response(String provider,JSONObject wire)throws Exception{
        if(OPENAI.equals(provider))return wire;
        JSONArray choices=wire.optJSONArray("choices");JSONObject choice=choices==null?null:choices.optJSONObject(0),message=choice==null?null:choice.optJSONObject("message");
        if(message==null||message.has("refusal")&&!message.isNull("refusal")||"content_filter".equals(choice.optString("finish_reason")))throw new IllegalStateException("Gemini could not answer this request.");
        JSONArray output=TvJson.array(TvJson.object("type","provider_message","message",message));
        if(!message.isNull("content")&&!message.optString("content").isEmpty())output.put(TvJson.object("type","message","providerProjection",true,"content",TvJson.array(TvJson.object("type","output_text","text",message.optString("content")))));
        JSONArray calls=message.optJSONArray("tool_calls");
        if(calls!=null)for(int i=0;i<calls.length();i++){
            JSONObject call=calls.getJSONObject(i),function=call.getJSONObject("function");
            if(call.optString("id").isEmpty())call.put("id",UUID.randomUUID().toString());
            output.put(TvJson.object("type","function_call","providerProjection",true,"call_id",call.optString("id"),"name",function.optString("name"),"arguments",function.optString("arguments")));
        }
        return TvJson.object("status","length".equals(choice.optString("finish_reason"))?"incomplete":"completed","output",output);
    }
    static String text(JSONObject response){
        StringBuilder text=new StringBuilder();JSONArray output=response.optJSONArray("output");
        if(output!=null)for(int i=0;i<output.length();i++){
            JSONObject item=output.optJSONObject(i);if(item==null||!"message".equals(item.optString("type")))continue;
            JSONArray content=item.optJSONArray("content");if(content!=null)for(int j=0;j<content.length();j++){JSONObject p=content.optJSONObject(j);if(p!=null&&"output_text".equals(p.optString("type"))){if(text.length()>0)text.append('\n');text.append(p.optString("text"));}}
        }
        return text.toString().trim();
    }
}
