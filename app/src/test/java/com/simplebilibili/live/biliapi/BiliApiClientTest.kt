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

    @Test
    fun parsesLiveAreasFromAreaListResponse() {
        val json = """
            {
              "code": 0,
              "data": {
                "data": [
                  {
                    "id": 1,
                    "name": "网游",
                    "list": [
                      { "id": 11, "name": "英雄联盟" },
                      { "id": 12, "name": "DOTA2" }
                    ]
                  },
                  {
                    "id": 2,
                    "name": "手游",
                    "list": [
                      { "id": 21, "name": "王者荣耀" }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val areas = BiliApiClient.parseLiveAreas(json)

        assertEquals(3, areas.size)
        assertEquals(1L, areas[0].parentId)
        assertEquals(11L, areas[0].areaId)
        assertEquals("网游 / 英雄联盟", areas[0].displayName)
        assertEquals(21L, areas[2].areaId)
    }

    @Test
    fun parsesLiveAreasWhenDataIsArray() {
        val json = """
            {
              "code": 0,
              "data": [
                {
                  "id": 9,
                  "name": "生活",
                  "list": [
                    { "id": 91, "name": "日常" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val areas = BiliApiClient.parseLiveAreas(json)

        assertEquals(1, areas.size)
        assertEquals(9L, areas.first().parentId)
        assertEquals(91L, areas.first().areaId)
        assertEquals("生活 / 日常", areas.first().displayName)
    }

    @Test
    fun parsesAreaLiveRoomsFromRoomListResponse() {
        val json = """
            {
              "code": 0,
              "data": {
                "list": [
                  {
                    "roomid": 501,
                    "title": "分类直播间",
                    "uname": "分类主播",
                    "live_status": 1
                  }
                ]
              }
            }
        """.trimIndent()

        val rooms = BiliApiClient.parseLiveRoomItems(json)

        assertEquals(1, rooms.size)
        assertEquals(501L, rooms.first().roomId)
        assertEquals("分类主播", rooms.first().uname)
        assertEquals("分类直播间", rooms.first().title)
    }

    @Test
    fun parsesAreaLiveRoomsFromNestedListResponse() {
        val json = """
            {
              "code": 0,
              "data": {
                "list": {
                  "data": [
                    {
                      "id": 701,
                      "title": "嵌套分类直播间",
                      "uname": "嵌套分类主播"
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val rooms = BiliApiClient.parseLiveRoomItems(json)

        assertEquals(1, rooms.size)
        assertEquals(701L, rooms.first().roomId)
        assertEquals("嵌套分类主播", rooms.first().uname)
        assertEquals("嵌套分类直播间", rooms.first().title)
    }

    @Test
    fun parsesSearchLiveRoomsFromSearchResponse() {
        val json = """
            {
              "code": 0,
              "data": {
                "result": {
                  "live_room": [
                    {
                      "roomid": 601,
                      "title": "搜索直播间",
                      "uname": "搜索主播",
                      "live_status": 1
                    },
                    {
                      "room_id": 602,
                      "title": "另一个结果",
                      "name": "搜索主播2",
                      "live_status": 1
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val rooms = BiliApiClient.parseSearchLiveRooms(json)

        assertEquals(2, rooms.size)
        assertEquals(601L, rooms[0].roomId)
        assertEquals("搜索主播", rooms[0].uname)
        assertEquals(602L, rooms[1].roomId)
        assertEquals("搜索主播2", rooms[1].uname)
    }
}
