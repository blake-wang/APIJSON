/*Copyright ©2016 TommyLemon(https://github.com/TommyLemon/APIJSON)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/

package zuo.biao.apijson.server;

import java.util.Random;
import java.util.concurrent.TimeoutException;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSONObject;

import apijson.demo.server.model.Login;
import apijson.demo.server.model.Password;
import apijson.demo.server.model.User;
import apijson.demo.server.model.Verify;
import zuo.biao.apijson.BaseModel;
import zuo.biao.apijson.JSON;
import zuo.biao.apijson.JSONResponse;
import zuo.biao.apijson.RequestMethod;
import zuo.biao.apijson.StringUtil;
import zuo.biao.apijson.server.exception.ConditionNotMatchException;
import zuo.biao.apijson.server.exception.ConflictException;

/**request receiver and controller
 * @author Lemon
 */
@RestController
@RequestMapping("")
public class Controller {

	@RequestMapping("head/{request}")
	public String head(@PathVariable String request) {
		return new Parser(RequestMethod.HEAD).parse(request);
	}

	@RequestMapping("get/{request}")
	public String get(@PathVariable String request) {
		return new Parser(RequestMethod.GET).parse(request);
	}


	@RequestMapping(value="post", method = org.springframework.web.bind.annotation.RequestMethod.POST)
	public String post(@RequestBody String request) {
		return new Parser(RequestMethod.POST).parse(request);
	}

	/**用POST方法HEAD，request和response都非明文，浏览器看不到，用于对安全性要求高的HEAD请求
	 * @param request
	 * @return
	 */
	@RequestMapping(value="post_head", method = org.springframework.web.bind.annotation.RequestMethod.POST)
	public String post_head(@RequestBody String request) {
		return new Parser(RequestMethod.POST_HEAD).parse(request);
	}
	/**用POST方法GET，request和response都非明文，浏览器看不到，用于对安全性要求高的GET请求
	 * @param request
	 * @return
	 */
	@RequestMapping(value="post_get", method = org.springframework.web.bind.annotation.RequestMethod.POST)
	public String post_get(@RequestBody String request) {
		return new Parser(RequestMethod.POST_GET).parse(request);
	}

	/**以下接口继续用POST接口是为了客户端方便，只需要做get，post请求。也可以改用实际对应的方法。
	 * post，put方法名可以改为add，update等更客户端容易懂的名称
	 */
	@RequestMapping(value="put", method = org.springframework.web.bind.annotation.RequestMethod.POST)
	public String put(@RequestBody String request) {
		return new Parser(RequestMethod.PUT).parse(request);
	}

	@RequestMapping(value="delete", method = org.springframework.web.bind.annotation.RequestMethod.POST)
	public String delete(@RequestBody String request) {
		return new Parser(RequestMethod.DELETE).parse(request);
	}














	@RequestMapping("post/authCode/{phone}")
	public String postAuthCode(@PathVariable String phone) {
		new Parser(RequestMethod.DELETE, true).parse(newVerifyRequest(newVerify(phone, 0)));

		JSONObject response = new Parser(RequestMethod.POST, true).parseResponse(
				newVerifyRequest(newVerify(phone, new Random().nextInt(9999) + 1000)));

		JSONObject verify = null;
		try {
			verify = response.getJSONObject("Verify");
		} catch (Exception e) {
			// TODO: handle exception
		}
		if (verify == null || verify.getIntValue("status") != 200) {
			new Parser(RequestMethod.DELETE, true).parseResponse(new JSONRequest(new Verify(phone)));
			return JSON.toJSONString(Parser.extendErrorResult(response, null));
		}

		return getAuthCode(phone);
	}

	@RequestMapping(value="post_get/authCode/{phone}", method = org.springframework.web.bind.annotation.RequestMethod.POST)
	public String getAuthCode(@PathVariable String phone) {
		return new Parser(RequestMethod.POST_GET).parse(newVerifyRequest(newVerify(phone, 0)));
	}

	@RequestMapping("check/authCode/{phone}/{code}")
	public String checkAuthCode(@PathVariable String phone, @PathVariable String code) {
		return JSON.toJSONString(checkVerify(phone, code));
	}
	
	/**校验验证码
	 * @param phone
	 * @param code
	 * @return
	 */
	public JSONObject checkVerify(String phone, String code) {
		JSONResponse response = new JSONResponse(new Parser(RequestMethod.POST_GET, true)
				.parseResponse(new JSONRequest(new Verify(phone)).setTag(Verify.class.getSimpleName())));
		Verify verify = response.getObject(Verify.class);
		//验证码过期
		if (verify != null && System.currentTimeMillis() - verify.getDate() > 60000) {
			new Parser(RequestMethod.DELETE, true).parseResponse(new JSONRequest(new Verify(phone))
					.setTag(Verify.class.getSimpleName()));
			return Parser.newErrorResult(new TimeoutException("验证码已过期！"));
		}
		
		return new JSONResponse(new Parser(RequestMethod.POST_HEAD).parseResponse(
				new JSONRequest(new Verify(phone, code))));
	}
	

	private JSONObject newVerify(String phone, int code) {
		JSONObject verify = new JSONObject(true);
		verify.put("id", phone);
		if (code > 0) {
			verify.put("code", code);
		}
		return verify;
	}
	private JSONObject newVerifyRequest(JSONObject verify) {
		return newRequest(verify, "Verify", true);
	}



	@RequestMapping("get/login/{typeString}/{phone}/{password}")
	public String login(@PathVariable String typeString, @PathVariable String phone, @PathVariable String password) {
		if (StringUtil.isPhone(phone) == false) {
			return JSON.toJSONString(Parser.newErrorResult(new IllegalArgumentException("手机号不合法！")));
		}
		if (StringUtil.isNotEmpty(password, true) == false) {
			return JSON.toJSONString(Parser.newErrorResult(new IllegalArgumentException("密码/验证码不合法！")));
		}

		//手机号是否已注册
		JSONObject requestObject = new Parser(RequestMethod.HEAD).parseResponse(
				new JSONRequest(new User().setPhone(phone)));
		JSONResponse response = new JSONResponse(requestObject).getJSONResponse(User.class.getSimpleName());
		if (JSONResponse.isSucceed(response) == false) {
			return JSON.toJSONString(response);
		}
		if(JSONResponse.isExist(response) == false) {
			return JSON.toJSONString(Parser.newErrorResult(new NullPointerException("手机号未注册")));
		}

		//校验凭证
		int type = Integer.valueOf(0 + StringUtil.getNumber(typeString));
		if (type == Login.TYPE_PASSWORD) {//password
			response = new JSONResponse(new Parser(RequestMethod.POST_HEAD).parseResponse(
					new JSONRequest(new Password(User.class.getSimpleName(), phone, password))));
		} else {//verify
			response = new JSONResponse(checkVerify(phone, password));
		}
		if (JSONResponse.isSucceed(response) == false) {
			return JSON.toJSONString(response);
		}
		response = response.getJSONResponse(type == Login.TYPE_PASSWORD ? Password.class.getSimpleName() : Verify.class.getSimpleName());
		if (JSONResponse.isExist(response) == false) {
			return JSON.toJSONString(Parser.newErrorResult(new ConditionNotMatchException("账号或密码错误")));
		}


		//根据phone获取User
		JSONObject result = new Parser().parseResponse(new JSONRequest(new User().setPhone(phone)));
		response = new JSONResponse(result);

		User user = response == null ? null : response.getObject(User.class);
		if (user == null || BaseModel.value(user.getId()) <= 0) {
			return JSON.toJSONString(Parser.extendErrorResult(result, null));
		}
		//删除Login
		new Parser(RequestMethod.DELETE, true).parseResponse(new JSONRequest(new Login().setUserId(user.getId())));
		//写入Login
		new Parser(RequestMethod.POST, true).parseResponse(new JSONRequest(
				new Login().setType(type).setUserId(user.getId())));

		return JSON.toJSONString(result);
	}



	@RequestMapping(value="post/register/user", method = org.springframework.web.bind.annotation.RequestMethod.POST)
	public String register(@RequestBody String request) {
		JSONObject requestObject = null;
		try {
			requestObject = Parser.getCorrectRequest(RequestMethod.POST
					, Parser.parseRequest(request, RequestMethod.POST));
		} catch (Exception e) {
			return JSON.toJSONString(Parser.newErrorResult(e));
		}

		JSONObject user = requestObject == null ? null : requestObject.getJSONObject("User");
		String phone = user == null ? null : user.getString("phone");
		if (StringUtil.isPhone(phone) == false) {
			return JSON.toJSONString(Parser.extendErrorResult(requestObject
					, new IllegalArgumentException("User.phone: " + phone + " 不合法！")));
		}
		String password = StringUtil.getString(requestObject.getString("password"));
		if (password.length() < 6) {
			return JSON.toJSONString(Parser.extendErrorResult(requestObject
					, new IllegalArgumentException("User.password: " + password + " 不合法！不能小于6个字符！")));
		}
		//		String verify = StringUtil.getString(user.getString("verify"));
		//		if (verify.length() < 4) {
		//			return JSON.toJSONString(Parser.extendErrorResult(requestObject
		//					, new IllegalArgumentException("User.verify: " + verify + " 不合法！不能小于6个字符！")));
		//		}

		JSONResponse response = new JSONResponse(checkVerify(phone, requestObject.getString("verify")));
		if (JSONResponse.isSucceed(response) == false) {
			return JSON.toJSONString(response);
		}
		//手机号或验证码错误
		if (JSONResponse.isExist(response.getJSONResponse(Verify.class.getSimpleName())) == false) {
			return JSON.toJSONString(Parser.extendErrorResult(response
					, new ConditionNotMatchException("手机号或验证码错误！")));
		}


		JSONObject check = new Parser(RequestMethod.HEAD)
				.parseResponse(newUserRequest(newUser(phone)));
		JSONObject checkUser = check == null ? null : check.getJSONObject("User");
		if (checkUser == null || checkUser.getIntValue("count") > 0) {
			return JSON.toJSONString(Parser.newErrorResult(new ConflictException("手机号" + phone + "已经注册")));
		}

		//生成User
		JSONObject result = new Parser(RequestMethod.POST, true).parseResponse(requestObject);
		response = new JSONResponse(result);
		if (JSONResponse.isSucceed(response) == false) {
			return JSON.toJSONString(Parser.extendErrorResult(result, null));
		}
		
		//生成Password
		response = new JSONResponse(new Parser(RequestMethod.POST, true).parseResponse(
				new JSONRequest(new Password(User.class.getSimpleName(), phone, password))));
		if (JSONResponse.isSucceed(response.getJSONResponse(Password.class.getSimpleName())) == false) {
			new Parser(RequestMethod.DELETE, true).parseResponse(new JSONRequest(new User().setPhone(phone)));
			new Parser(RequestMethod.DELETE, true).parseResponse(new JSONRequest(new Password().setPhone(phone)));
			return JSON.toJSONString(Parser.extendErrorResult(result, null));
		}

		return JSON.toJSONString(result);
	}





	private JSONObject newUser(String phone) {
		JSONObject verify = new JSONObject(true);
		verify.put("phone", phone);
		return verify;
	}
	private JSONObject newUserRequest(JSONObject user) {
		return newRequest(user, "User", true);
	}


	private JSONObject newRequest(JSONObject object, String name, boolean needTag) {
		JSONObject request = new JSONObject(true);
		request.put(name, object);
		if (needTag) {
			request.put(JSONRequest.KEY_TAG, name);
		}
		return request;
	}




}
