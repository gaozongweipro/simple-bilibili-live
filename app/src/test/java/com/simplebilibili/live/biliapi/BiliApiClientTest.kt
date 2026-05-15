package com.simplebilibili.live.biliapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliApiClientTest {
    @Test
    fun parsesLiveRoomFromInfoResponse() {
        val json = """
            {
              "code": 0,
              "data": {
                "room_id": 22889451,
                "title": "测试直播间",
                "live_status": 1
              }
            }
        """.trimIndent()

        val room = BiliApiClient.parseRoomInfo(123L, json)

        assertEquals(123L, room.inputRoomId)
        assertEquals(22889451L, room.roomId)
        assertEquals("测试直播间", room.title)
        assertEquals(LiveStatus.LIVE, room.liveStatus)
    }

    @Test
    fun parsesOfflineRoomFromInfoResponse() {
        val json = """
            {
              "code": 0,
              "data": {
                "room_id": 88,
                "title": "未开播",
                "live_status": 0
              }
            }
        """.trimIndent()

        val room = BiliApiClient.parseRoomInfo(88L, json)

        assertEquals(LiveStatus.OFFLINE, room.liveStatus)
    }

    @Test
    fun parsesStreamUrlsFromPlayInfoResponse() {
        val json = """
            {
              "code": 0,
              "data": {
                "playurl_info": {
                  "playurl": {
                    "stream": [
                      {
                        "format": [
                          {
                            "codec": [
                              {
                                "current_qn": 400,
                                "base_url": "/live-bvc/test.flv",
                                "url_info": [
                                  {
                                    "host": "https://example.com",
                                    "extra": "?token=abc"
                                  }
                                ]
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        val stream = BiliApiClient.parsePlayInfo(22889451L, json)

        assertEquals(22889451L, stream.roomId)
        assertEquals(1, stream.options.size)
        assertEquals(400, stream.options.first().quality)
        assertTrue(stream.options.first().url.startsWith("https://example.com/live-bvc/test.flv"))
    }

    @Test
    fun parsesFollowedLiveRoomsFromWebListResponse() {
        val json = """
            {
              "code": 0,
              "data": {
                "rooms": [
                  {
                    "roomid": 101,
                    "title": "正在直播 A",
                    "uname": "主播 A",
                    "live_status": 1
                  },
                  {
                    "room_id": 202,
                    "title": "正在直播 B",
                    "uname": "主播 B",
                    "live_status": 1
                  },
                  {
                    "roomid": 303,
                    "title": "未开播",
                    "uname": "主播 C",
                    "live_status": 0
                  }
                ]
              }
            }
        """.trimIndent()

        val rooms = BiliApiClient.parseFollowedLiveRooms(json)

        assertEquals(2, rooms.size)
        assertEquals(101L, rooms[0].roomId)
        assertEquals("主播 A", rooms[0].uname)
        assertEquals("正在直播 A", rooms[0].title)
        assertEquals(202L, rooms[1].roomId)
    }

    @Test
    fun parsesFollowedLiveRoomsFromListResponse() {
        val json = """
            {
              "code": 0,
              "data": {
                "list": [
                  {
                    "room_id": 404,
                    "title": "列表直播",
                    "uname": "主播 D",
                    "live_status": 1
                  }
                ]
              }
            }
        """.trimIndent()

        val rooms = BiliApiClient.parseFollowedLiveRooms(json)

        assertEquals(1, rooms.size)
        assertEquals(404L, rooms.first().roomId)
        assertEquals("主播 D", rooms.first().uname)
    }
}
