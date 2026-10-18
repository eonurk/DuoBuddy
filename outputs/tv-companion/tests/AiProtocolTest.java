package family.tv.companion;
import org.json.*;
public final class AiProtocolTest {
  static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
  public static void main(String[] args)throws Exception{
    JSONArray input=TvJson.array(TvJson.object("role","user","content","Play music"));
    JSONArray tools=TvJson.array(TvJson.object("type","function","name","media","description","Media","parameters",TvJson.object("type","object")));
    JSONObject open=AiProtocol.request("openai",AiProtocol.LUNA,"instructions",input,tools);
    check(!open.getBoolean("store"),"OpenAI storage disabled");check(open.getJSONObject("reasoning").getString("effort").equals("none"),"fast reasoning");
    JSONObject signed=TvJson.object("role","assistant","content",JSONObject.NULL,"tool_calls",TvJson.array(TvJson.object("id","call1","type","function","function",TvJson.object("name","media","arguments","{\"command\":\"play\"}"),"extra_content",TvJson.object("google",TvJson.object("thought_signature","signature-must-survive")))));
    JSONObject normalized=AiProtocol.response("gemini",TvJson.object("choices",TvJson.array(TvJson.object("message",signed,"finish_reason","tool_calls"))));
    JSONArray output=normalized.getJSONArray("output");for(int i=0;i<output.length();i++)input.put(output.get(i));input.put(TvJson.object("type","function_call_output","call_id","call1","output","{\"message\":\"Sent\"}"));
    JSONObject next=AiProtocol.request("gemini",AiProtocol.FLASH,"instructions",input,tools);JSONArray messages=next.getJSONArray("messages");
    check(messages.length()==4,"no duplicate projected messages");check(messages.getJSONObject(2).toString().equals(signed.toString()),"signed message exact round trip");check(messages.getJSONObject(3).getString("tool_call_id").equals("call1"),"tool result association");
    check(next.getString("reasoning_effort").equals("minimal"),"fast Gemini reasoning");
    JSONObject text=AiProtocol.response("gemini",TvJson.object("choices",TvJson.array(TvJson.object("message",TvJson.object("role","assistant","content","Hello"),"finish_reason","stop"))));check(AiProtocol.text(text).equals("Hello"),"text projection");
    JSONObject incomplete=AiProtocol.response("gemini",TvJson.object("choices",TvJson.array(TvJson.object("message",signed,"finish_reason","length"))));check(incomplete.getString("status").equals("incomplete"),"truncation reported");
    for(String invalid:new String[]{"../escape","id?key=secret","","space model"}){try{AiProtocol.validateSelection("openai",invalid);throw new AssertionError("invalid ID accepted");}catch(IllegalArgumentException expected){}}
    try{AiProtocol.validateSelection("other","model");throw new AssertionError("unknown provider accepted");}catch(IllegalArgumentException expected){}
    try{AiProtocol.response("gemini",TvJson.object("choices",new JSONArray()));throw new AssertionError("empty response accepted");}catch(IllegalStateException expected){}
    System.out.println("Provider protocol regression checks passed");
  }
}
