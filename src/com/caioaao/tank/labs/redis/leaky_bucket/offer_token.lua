-- Assumes state is up to date
local token_count = tonumber(redis.call('get', _:counter-key)) or 0
if token_count < tonumber(_:capacity) then
   return redis.call('incr', _:counter-key)
else
   return nil
end
