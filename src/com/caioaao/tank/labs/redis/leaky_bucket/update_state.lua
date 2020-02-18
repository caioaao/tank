local last_update = redis.call('getset', _:update-time-key, _:as-of) or _:as-of
local leaked = math.floor((_:as-of - last_update) / _:leak-ms)
local new_count = math.max(0, (redis.call('get', _:counter-key) or 0) - leaked)
redis.call('set', _:counter-key, new_count)
return new_count
