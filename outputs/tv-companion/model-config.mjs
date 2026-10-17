export const DEFAULT_MODEL='gpt-6-luna';
export function responseRequest({model=DEFAULT_MODEL,instructions,input,tools}){
  // A fixed cache key keeps every Buddy request on the same prompt cache, so the
  // unchanging tools + persona prefix is billed at the cached-input rate.
  const request={model,instructions,input,tools,parallel_tool_calls:false,max_output_tokens:2000,store:true,prompt_cache_key:'duobook-buddy'};
  // Preserve the fast, non-reasoning role of the original voice controller.
  // GPT-6 Luna explicitly supports none with Responses function calling.
  if(model==='gpt-6-luna'){
    request.reasoning={effort:'none'};
    request.include=['reasoning.encrypted_content'];
  }
  return request;
}
