local userId = ARGV[1]
local voucherId = ARGV[2]
local currentTimeMills = tonumber(ARGV[3])

local stockKey = KEYS[1] .. voucherId
local beginTimeMillsKey = KEYS[3] .. voucherId
local endTimeMillsKey = KEYS[4] .. voucherId
local orderKey = KEYS[2] .. voucherId

-- 秒杀时间
if (tonumber(redis.call("get", beginTimeMillsKey)) > tonumber(currentTimeMills) or tonumber(redis.call("get", endTimeMillsKey)) < tonumber(currentTimeMills)) then
    --不在活动时间
    return 1;
end
-- 重复下单
if (tonumber(redis.call("sismember", orderKey, userId)) == 1) then
    return 2;
end
-- 库存
if (tonumber(redis.call("get", stockKey)) <= 0) then
    return 3;
end
-- 下单
redis.call("incrby",stockKey, -1)
redis.call("sadd", orderKey, userId)
return 0