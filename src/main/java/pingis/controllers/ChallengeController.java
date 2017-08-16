package pingis.controllers;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;
import pingis.entities.Challenge;
import pingis.entities.ChallengeType;
import pingis.entities.Task;
import pingis.entities.TaskInstance;
import pingis.entities.TaskType;
import pingis.entities.User;
import pingis.services.ChallengeService;
import pingis.services.EditorService;
import pingis.services.TaskInstanceService;
import pingis.services.TaskService;
import pingis.services.UserService;

@Controller
public class ChallengeController {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Autowired
  ChallengeService challengeService;
  @Autowired
  TaskService taskService;
  @Autowired
  TaskInstanceService taskInstanceService;
  @Autowired
  UserService userService;

  @RequestMapping(value = "/newchallenge")
  public String newChallenge(Model model) {

    return "newchallenge";
  }

  @RequestMapping(value = "/createChallenge", method = RequestMethod.POST)
  public RedirectView createChallenge(String challengeName, String challengeDesc,
      String challengeType, String realm, RedirectAttributes redirectAttributes) {

    Challenge newChallenge = new Challenge(challengeName,
        userService.getCurrentUser(),
        challengeDesc, challengeType == "PROJECT" ? ChallengeType.PROJECT : ChallengeType.MIXED);
    newChallenge.setLevel(1);
    newChallenge = challengeService.save(newChallenge);
    logger.info(newChallenge.toString());

    return new RedirectView("/newtaskpair");
  }

  @RequestMapping(value = "/newtaskpair")
  public String newTaskPair(Model model) {
    return "newtaskpair";
  }

  @RequestMapping(value = "/createTaskPair", method = RequestMethod.POST)
  public RedirectView createTaskPair(String testTaskName, String implementationTaskName,
      String testTaskDesc, String implementationTaskDesc,
      String testCodeStub, String implementationCodeStub,
      long challengeId,
      RedirectAttributes redirectAttributes) {
    // create and save new tasks, redirect to createtaskinstance
    Challenge currentChallenge = challengeService.findOne(challengeId);
    int numberOfTasks = challengeService.getNumberOfTasks(currentChallenge);
    int nextIndex = numberOfTasks / 2 + 1;
    User currentUser = userService.getCurrentUser();
    Task testTask = new Task(nextIndex,
        TaskType.TEST,
        currentUser,
        testTaskName,
        testTaskDesc,
        testCodeStub,
        1, 1);
    Task implTask = new Task(nextIndex,
        TaskType.IMPLEMENTATION,
        currentUser,
        implementationTaskName,
        implementationTaskDesc,
        implementationCodeStub,
        1, 1);
    currentChallenge.addTask(testTask);
    currentChallenge.addTask(implTask);
    testTask.setChallenge(currentChallenge);
    implTask.setChallenge(currentChallenge);
    taskService.save(testTask);
    taskService.save(implTask);
    redirectAttributes.addAttribute("taskId", testTask.getId());
    redirectAttributes.addAttribute("testTaskInstanceId", 0L);
    return new RedirectView("/newTaskInstance");
  }

  @RequestMapping(value = "/playTurn/{challengeId}")
  public RedirectView playTurn(Model model, Long challengeId,
      RedirectAttributes redirectAttributes) {
    Challenge currentChallenge = challengeService.findOne(challengeId);
    User currentUser = userService.getCurrentUser();
    int index = challengeService.getNumberOfTasks(currentChallenge) / 2;
    if (!challengeService.isParticipating(currentChallenge)) {
      redirectAttributes.addAttribute("message", "naaaaaughty!");
      return new RedirectView("/error");
    }
    TaskInstance unfinished = challengeService.getUnfinishedTaskInstance(currentChallenge);
    if (unfinished != null) {
      redirectAttributes.addAttribute("taskInstanceId", unfinished.getId());
      return new RedirectView("/task");
    }

    if (challengeService.isTestTurnInLiveChallenge(currentChallenge)) {
      return new RedirectView("/newtaskpair");
    }

    if (challengeService.isImplementationTurnInLiveChallenge(currentChallenge)) {
      Task implTask = challengeService.getTopmostImplementationTask(currentChallenge, index);
      Task testTask = challengeService.getTopmostTestTask(currentChallenge, index);
      redirectAttributes.addAttribute("taskId", implTask.getId());
      redirectAttributes.addAttribute("testTaskInstanceId",
          taskInstanceService.getByTaskAndUser(testTask, testTask.getAuthor()).getId());
      return new RedirectView("/newTaskInstance");
    }

    return new RedirectView("/user");

  }

}

